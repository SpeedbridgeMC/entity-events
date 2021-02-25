package adudecalledleo.speedbridge.entityevents.impl;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
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
import org.objectweb.asm.tree.ClassNode;

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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class EntityClassScanner {
    private static final class MixinChecker extends ClassVisitor {
        public boolean isMixin;

        public MixinChecker() {
            super(Opcodes.ASM7);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            isMixin = false;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if ("Lorg/spongepowered/asm/mixin/Mixin;".equals(desc))
                isMixin = true;
            return super.visitAnnotation(desc, visible);
        }
    }

    private static final class ReusableClassNode extends ClassNode {
        public ReusableClassNode() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            // reset everything!
            clearList(visibleAnnotations);
            clearList(invisibleAnnotations);
            clearList(visibleTypeAnnotations);
            clearList(invisibleTypeAnnotations);
            clearList(attrs);
            clearList(innerClasses);
            clearList(nestMembers);
            clearList(permittedSubclasses);
            clearList(recordComponents);
            clearList(fields);
            clearList(methods);
        }

        private <T> void clearList(@Nullable List<T> list) {
            if (list != null)
                list.clear();
        }
    }

    private static final Logger LOGGER = LogManager.getLogger("EntityEvents|EntityClassScanner");
    public final ObjectOpenHashSet<String> entityClassNames = new ObjectOpenHashSet<>();
    private final ReusableClassNode theOneClassNode = new ReusableClassNode();
    private final MixinChecker mixinChecker = new MixinChecker();
    private boolean addedNewEntriesLastPass = false;
    private final @Nullable MessageDigest md5Disgest;

    public EntityClassScanner() {
        MessageDigest tempDigest = null;
        try {
            tempDigest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("Failed to get MD5 MessageDigest instance! File checksums will not be available", e);
        }
        md5Disgest = tempDigest;
    }

    private static final String CHECKSUM_DIRECTORY = "DIRECTORY";

    private @Nullable String getModFileChecksum(@NotNull ModContainer mod) {
        final String modId = mod.getMetadata().getId();
        if (md5Disgest != null) {
            if (mod instanceof net.fabricmc.loader.ModContainer) {
                URL originURL = ((net.fabricmc.loader.ModContainer) mod).getOriginUrl();
                URI originURI = null;
                try {
                    originURI = originURL.toURI();
                } catch (URISyntaxException e) {
                    LOGGER.error("Failed to convert URL \"" + originURL + "\" to URL!", e);
                }
                if (originURI != null) {
                    Path path = Paths.get(originURI);
                    if (Files.isDirectory(path))
                        return CHECKSUM_DIRECTORY;
                    try (InputStream input = Files.newInputStream(path)) {
                        byte[] buf = new byte[1024];
                        int count;
                        while ((count = input.read(buf)) > 0) {
                            md5Disgest.update(buf, 0, count);
                        }
                        buf = md5Disgest.digest();
                        md5Disgest.reset();
                        StringBuilder sb = new StringBuilder();
                        for (byte b : buf)
                            sb.append(String.format("%02X", b));
                        return sb.toString().toUpperCase(Locale.ROOT);
                    } catch (IOException e) {
                        LOGGER.error("Failed to read from mod file \"" + path + "\"!", e);
                    }
                }
            }
            LOGGER.error("Failed to determine the file checksum of mod \"{}\" - please delete its entry in the cache if you update it!", modId);
        }
        return null;
    }

    public void scan() {
        ScanResultCache cache = new ScanResultCache();
        cache.load();

        int cachedCount = 0;
        ObjectOpenHashSet<ModContainer> modsToScan = new ObjectOpenHashSet<>();
        Object2ReferenceOpenHashMap<String, String> modChecksums = new Object2ReferenceOpenHashMap<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String modId = mod.getMetadata().getId();
            if ("java".equals(modId))
                // hardcoded exception :P
                continue;
            String fileChecksum = getModFileChecksum(mod);
            if (CHECKSUM_DIRECTORY.equals(fileChecksum))
                // always rescan directories (dev folders)
                modsToScan.add(mod);
            else {
                modChecksums.put(modId, fileChecksum);
                ScanResultCache.Entry cachedEntry = cache.getEntry(modId);
                if (cachedEntry == null || !StringUtils.equalsIgnoreCase(fileChecksum, cachedEntry.fileChecksum))
                    modsToScan.add(mod);
                else
                    cachedCount += cachedEntry.entitySubclasses.size();
            }
        }
        if (cachedCount > 0) {
            LOGGER.info("Loaded {} Entity subclass names from the cache.", cachedCount);
            // we don't care about mod IDs or file hashes, just give use the entity class names
            cache.getAllEntries().stream().flatMap(entry -> entry.entitySubclasses.stream()).forEach(entityClassNames::add);
        }

        if (!modsToScan.isEmpty()) {
            LOGGER.info("Now scanning for Entity subclasses in {} mods...", modsToScan.size());
            Object2ReferenceOpenHashMap<String, ImmutableSet.Builder<String>> setBuilders = new Object2ReferenceOpenHashMap<>();
            int pass = 1;
            int count = 0;
            Stopwatch stopwatch = Stopwatch.createStarted();
            do {
                if (pass > 20)
                    throw new RuntimeException("Took too long to scan!");
                addedNewEntriesLastPass = false;
                LOGGER.debug(" == SCANNING FOR ENTITY CLASSES, PASS {} == ", pass);
                for (ModContainer mod : modsToScan) {
                    LOGGER.debug("Scanning in mod \"{}\"", mod.getMetadata().getId());
                    ImmutableSet.Builder<String> builder = setBuilders.computeIfAbsent(mod.getMetadata().getId(), s -> ImmutableSet.builder());
                    count += scanDirectoryOrFile(mod.getRootPath(), builder::add);
                }
                if (addedNewEntriesLastPass)
                    pass++;
                else
                    LOGGER.debug(" == FINISHED ENTITY CLASS SCAN IN {} PASSES == ", pass);
            } while (addedNewEntriesLastPass);
            LOGGER.info("Found {} Entity subclasses in {}ms!", count, stopwatch.stop().elapsed(TimeUnit.MILLISECONDS));

            for (Object2ReferenceMap.Entry<String, ImmutableSet.Builder<String>> entry : setBuilders.object2ReferenceEntrySet())
                cache.putEntry(new ScanResultCache.Entry(entry.getKey(), modChecksums.get(entry.getKey()), entry.getValue().build()));
        }

        cache.save();
    }

    private int scanDirectoryOrFile(@NotNull Path path, @NotNull Consumer<String> resultConsumer) {
        if (Files.isDirectory(path)) {
            //System.out.println("Scanning children of directory \"" + path + "\"");
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
                if (!entityClassNames.contains(quickClassName)) {
                    //System.out.println("Scanning class \"" + path + "\"");
                    try (InputStream is = Files.newInputStream(path)) {
                        if (scanClass(is, resultConsumer))
                            return 1;
                    } catch (IOException e) {
                        LOGGER.error("Failed to scan class \"" + path.toString() + "\"!", e);
                    }
                }
            }
            return 0;
        }
    }

    private boolean scanClass(@NotNull InputStream is, @NotNull Consumer<String> resultConsumer) throws IOException {
        ClassReader reader = new ClassReader(is);
        String className = reader.getClassName();
        if (MappedNames.CLASS_ENTITY.equals(className) || entityClassNames.contains(reader.getSuperName())) {
            reader.accept(theOneClassNode, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            theOneClassNode.accept(mixinChecker);
            if (!mixinChecker.isMixin) {
                if (entityClassNames.add(className)) {
                    LOGGER.debug("Adding \"" + className + "\" as entity class");
                    resultConsumer.accept(className);
                    addedNewEntriesLastPass = true;
                    return true;
                }
            }
        }
        return false;
    }
}
