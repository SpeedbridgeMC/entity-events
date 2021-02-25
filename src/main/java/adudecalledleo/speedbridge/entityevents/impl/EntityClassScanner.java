package adudecalledleo.speedbridge.entityevents.impl;

import com.google.common.base.Stopwatch;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class EntityClassScanner {
    private static final class MixinChecker extends ClassVisitor {
        public static final MixinChecker INSTANCE = new MixinChecker();

        public boolean isMixin;

        private MixinChecker() {
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

    public static final ObjectOpenHashSet<String> ENTITY_CLASS_NAMES = new ObjectOpenHashSet<>();
    private static final ReusableClassNode THE_ONE_CLASS_NODE = new ReusableClassNode();
    private static boolean addedNewEntriesLastPass;

    public static void scan() {
        // TODO cache results!!!
        System.out.println("Entity Events is now scanning for Entity subclasses...");
        int pass = 1;
        Stopwatch stopwatch = Stopwatch.createStarted();
        do {
            if (pass > 20)
                throw new RuntimeException("Took too long to scan!");
            addedNewEntriesLastPass = false;
            System.out.println(" == SCANNING FOR ENTITY CLASSES, PASS " + pass + " == ");
            for (ModContainer mod : FabricLoader.getInstance().getAllMods())
                scanDirectoryOrFile(mod.getRootPath());
            if (addedNewEntriesLastPass)
                pass++;
            else
                System.out.println(" == FINISHED ENTITY CLASS SCAN IN " + pass + " PASSES == ");
        } while (addedNewEntriesLastPass);
        System.out.format("Found %d Entity subclasses in %dms!%n", ENTITY_CLASS_NAMES.size(), stopwatch.stop().elapsed(TimeUnit.MILLISECONDS));
    }

    private static void scanDirectoryOrFile(@NotNull Path path) {
        if (Files.isDirectory(path)) {
            //System.out.println("Scanning children of directory \"" + path + "\"");
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(path)) {
                for (Path child : ds)
                    scanDirectoryOrFile(child);
            } catch (IOException e) {
                System.err.println("Failed to scan directory \"" + path.toString() + "\"!");
                e.printStackTrace();
            }
        } else {
            String fileName = path.getFileName().toString();
            if (fileName.endsWith(".class")) {
                String pathString = path.toString();
                String quickClassName = pathString.substring(1, pathString.length() - ".class".length());
                if (ENTITY_CLASS_NAMES.contains(quickClassName))
                    // this class has already been added to the entity class list, don't bother rescanning it
                    return;
                //System.out.println("Scanning class \"" + path + "\"");
                try (InputStream is = Files.newInputStream(path)) {
                    scanClass(is);
                } catch (IOException e) {
                    System.err.println("Failed to scan class \"" + path.toString() + "\"!");
                    e.printStackTrace();
                }
            }
        }
    }

    private static void scanClass(@NotNull InputStream is) throws IOException {
        ClassReader reader = new ClassReader(is);
        String className = reader.getClassName();
        if (MappedNames.CLASS_ENTITY.equals(className) || ENTITY_CLASS_NAMES.contains(reader.getSuperName())) {
            reader.accept(THE_ONE_CLASS_NODE, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            THE_ONE_CLASS_NODE.accept(MixinChecker.INSTANCE);
            if (!MixinChecker.INSTANCE.isMixin) {
                if (ENTITY_CLASS_NAMES.add(className)) {
                    System.out.println("Adding " + className + " as entity class");
                    addedNewEntriesLastPass = true;
                }
            }
        }
    }
}
