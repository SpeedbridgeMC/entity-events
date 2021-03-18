package io.github.speedbridgemc.entityevents.impl;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class EntityClassScanner {
    private static final class MixinChecker extends ClassVisitor {
        public boolean isMixin;

        public MixinChecker() {
            super(Opcodes.ASM8);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            isMixin = false;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if ("Lorg/spongepowered/asm/mixin/Mixin;".equals(desc))
                isMixin = true;
            return super.visitAnnotation(desc, visible);
        }
    }

    private static final Logger LOGGER = LogManager.getLogger("EntityEvents|EntityClassScanner");
    public final ObjectOpenHashSet<String> entityClassNames = new ObjectOpenHashSet<>();
    private final ObjectSet<String> entityClassNamesSync = ObjectSets.synchronize(entityClassNames);
    private final ObjectSet<String> mixinClassNames = ObjectSets.synchronize(new ObjectOpenHashSet<>());
    private final ThreadLocal<MixinChecker> tlChecker = ThreadLocal.withInitial(MixinChecker::new);
    private final AtomicBoolean foundNewEntityThisPass = new AtomicBoolean(false);
    private final @Nullable MessageDigest checksumDigest;

    public EntityClassScanner() {
        MessageDigest tempDigest = null;
        try {
            tempDigest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("Failed to get SHA-256 MessageDigest instance! File checksums will not be available", e);
        }
        checksumDigest = tempDigest;
    }

    private static final class ChecksumResult {
        private final boolean directory;
        private final @Nullable String checksum;

        private ChecksumResult(boolean directory, @Nullable String checksum) {
            this.directory = directory;
            this.checksum = checksum;
        }

        private static final ChecksumResult DIRECTORY = new ChecksumResult(true, null);
        private static final ChecksumResult FAILURE = new ChecksumResult(false, null);

        public static @NotNull ChecksumResult directory() {
            return DIRECTORY;
        }

        public static @NotNull ChecksumResult failure() {
            return FAILURE;
        }

        public static @NotNull ChecksumResult success(@NotNull String checksum) {
            return new ChecksumResult(false, checksum);
        }

        @SuppressWarnings("UnusedReturnValue")
        public @NotNull ChecksumResult ifDirectory(@NotNull Runnable runnable) {
            if (directory)
                runnable.run();
            return this;
        }

        @SuppressWarnings("UnusedReturnValue")
        public @NotNull ChecksumResult ifPresent(@NotNull Consumer<@NotNull String> consumer) {
            if (checksum != null)
                consumer.accept(checksum);
            return this;
        }
    }

    private @NotNull ChecksumResult calculateModChecksum(@NotNull ModContainer mod) {
        if (checksumDigest == null)
            return ChecksumResult.failure();
        final String modId = mod.getMetadata().getId();
        if (mod instanceof net.fabricmc.loader.ModContainer) {
            URL originURL = ((net.fabricmc.loader.ModContainer) mod).getOriginUrl();
            URI originURI = null;
            try {
                originURI = originURL.toURI();
            } catch (URISyntaxException e) {
                LOGGER.error("Failed to convert URL \"" + originURL + "\" to URI!", e);
            }
            if (originURI != null) {
                Path path = Paths.get(originURI);
                if (Files.isDirectory(path))
                    return ChecksumResult.directory();
                try (InputStream input = Files.newInputStream(path);
                     BufferedInputStream bInput = new BufferedInputStream(input)) {
                    byte[] buf = new byte[1024];
                    int count;
                    while ((count = bInput.read(buf)) > 0) {
                        checksumDigest.update(buf, 0, count);
                    }
                    buf = checksumDigest.digest();
                    checksumDigest.reset();
                    StringBuilder sb = new StringBuilder();
                    for (byte b : buf)
                        sb.append(String.format("%02X", b));
                    return ChecksumResult.success(sb.toString().toUpperCase(Locale.ROOT));
                } catch (IOException e) {
                    LOGGER.error("Failed to read from mod file \"" + path + "\"!", e);
                }
            }
        }
        LOGGER.error("Failed to determine the file checksum of mod \"{}\" - please delete its entry in the cache if you update it!", modId);
        return ChecksumResult.failure();
    }

    public void scan() {
        ScanResultCache cache = new ScanResultCache();
        cache.load();

        final int[] cachedCount = { 0 };
        ObjectOpenHashSet<ModContainer> modsToScan = new ObjectOpenHashSet<>();
        Object2ReferenceOpenHashMap<String, String> modChecksums = new Object2ReferenceOpenHashMap<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String modId = mod.getMetadata().getId();
            // hardcoded exceptions :P
            if ("fabricloader".equals(modId) || "java".equals(modId))
                continue;
            calculateModChecksum(mod).ifPresent(checksum -> {
                modChecksums.put(modId, checksum);
                ScanResultCache.Entry cachedEntry = cache.getEntry(modId);
                if (cachedEntry == null || !StringUtils.equalsIgnoreCase(checksum, cachedEntry.fileChecksum)) {
                    cache.removeEntry(modId);
                    modsToScan.add(mod);
                } else
                    cachedCount[0] += cachedEntry.entitySubclasses.size();
            }).ifDirectory(() -> modsToScan.add(mod)); // always rescan folder mods (dev environment)
        }
        if (cachedCount[0] > 0) {
            LOGGER.info("Loaded {} Entity subclass names from the cache.", cachedCount[0]);
            // we don't care about mod IDs or file checksums, just give us the entity class names
            cache.getAllEntries().stream().flatMap(entry -> entry.entitySubclasses.stream()).forEach(entityClassNames::add);
        }

        if (!modsToScan.isEmpty()) {
            LOGGER.info("Now scanning for Entity subclasses in {} mods...", modsToScan.size());
            MappedNames.initialize(); // load this class before we start threadin', because apparently Knot can deadlock when loading classes
            ExecutorService executorService = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors());
            Object2ReferenceOpenHashMap<String, ImmutableSet.Builder<String>> setBuilders = new Object2ReferenceOpenHashMap<>();
            Object2ReferenceMap<String, ImmutableSet.Builder<String>> setBuildersSync = Object2ReferenceMaps.synchronize(setBuilders);

            int countSrc = 0;
            // add root Entity class to save some time
            if (entityClassNames.add(MappedNames.CLASS_ENTITY)) {
                // add it to the cache manually lmao
                ImmutableSet.Builder<String> minecraftClassesBuilder = ImmutableSet.builder();
                minecraftClassesBuilder.add(MappedNames.CLASS_ENTITY);
                setBuilders.put("minecraft", minecraftClassesBuilder);
                countSrc = 1;
            }

            int pass = 1;
            AtomicInteger count = new AtomicInteger(countSrc);
            Stopwatch stopwatch = Stopwatch.createStarted();
            do {
                if (pass > 20)
                    throw new RuntimeException("Took too long to scan!");
                foundNewEntityThisPass.set(false);
                LOGGER.debug(" == SCANNING FOR ENTITY CLASSES, PASS {} == ", pass);
                ReferenceOpenHashSet<Callable<Void>> callables = new ReferenceOpenHashSet<>();
                for (ModContainer mod : modsToScan) {
                    callables.add(() -> {
                        LOGGER.debug("Scanning in mod \"{}\"", mod.getMetadata().getId());
                        ImmutableSet.Builder<String> builder = setBuildersSync.computeIfAbsent(mod.getMetadata().getId(), s -> ImmutableSet.builder());
                        count.addAndGet(scanDirectoryOrFile(mod.getRootPath(), builder::add));
                        return null;
                    });
                }
                try {
                    executorService.invokeAll(callables);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Pass execution was interrupted!", e);
                }
                if (foundNewEntityThisPass.get())
                    pass++;
                else
                    LOGGER.debug(" == FINISHED ENTITY CLASS SCAN IN {} PASSES == ", pass);
            } while (foundNewEntityThisPass.get());
            LOGGER.info("Found {} Entity subclasses in {}ms!", count.get(), stopwatch.stop().elapsed(TimeUnit.MILLISECONDS));
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(3, TimeUnit.SECONDS))
                    executorService.shutdownNow();
            } catch (InterruptedException ignored) { }

            for (Object2ReferenceMap.Entry<String, ImmutableSet.Builder<String>> entry : setBuilders.object2ReferenceEntrySet())
                cache.putEntry(new ScanResultCache.Entry(entry.getKey(), modChecksums.get(entry.getKey()), entry.getValue().build()));
        }

        cache.save();
        entityClassNames.trim();
    }

    private int scanDirectoryOrFile(@NotNull Path path, @NotNull Consumer<String> resultConsumer) {
        if (Files.isDirectory(path)) {
            LOGGER.trace("Scanning children of directory \"{}\"", path);
            int sum = 0;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(path)) {
                for (Path child : ds)
                    sum += scanDirectoryOrFile(child, resultConsumer);
            } catch (IOException e) {
                LOGGER.error("Failed to scan directory \"" + path.toString() + "\"!", e);
            }
            return sum;
        } else {
            String fileName = path.getFileName().toString();
            if (fileName.endsWith(".class")) {
                String pathString = path.toString();
                String quickClassName = pathString.substring(1, pathString.length() - ".class".length());
                if (mixinClassNames.contains(quickClassName) || entityClassNamesSync.contains(quickClassName))
                    return 0;
                LOGGER.trace("Scanning class \"{}\"", path);
                try (InputStream is = Files.newInputStream(path)) {
                    if (scanClass(is, resultConsumer))
                        return 1;
                } catch (IOException e) {
                    LOGGER.error("Failed to scan class \"" + path.toString() + "\"!", e);
                }
            }
            return 0;
        }
    }

    private boolean scanClass(@NotNull InputStream is, @NotNull Consumer<String> resultConsumer) throws IOException {
        ClassReader reader = new ClassReader(is);
        String className = reader.getClassName();
        if (entityClassNamesSync.contains(reader.getSuperName())) {
            MixinChecker checker = tlChecker.get();
            reader.accept(checker, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            if (checker.isMixin) {
                mixinClassNames.add(className);
                return false;
            } else if (entityClassNamesSync.add(className)) {
                LOGGER.debug("Found entity subclass \"{}\"", className);
                resultConsumer.accept(className);
                foundNewEntityThisPass.set(true);
                return true;
            }
        }
        return false;
    }
}
