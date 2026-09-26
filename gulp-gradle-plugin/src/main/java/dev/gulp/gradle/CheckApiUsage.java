package dev.gulp.gradle;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;

/**
 * Fails when game code uses the engine outside {@code dev.gulp.api} (section 6): {@code dev.gulp.core}, the platform
 * SPI or a backend. Reads the compiled classes, so imports, fully qualified names and signatures all count; classes of
 * the game itself are allowed even under {@code dev.gulp}. {@code check} runs it.
 *
 * <pre>{@code
 * ./gradlew checkApiUsage
 * // com/example/Hud.class uses dev.gulp.core.ui.UiImpl: game code may use dev.gulp.api only
 * }</pre>
 */
public abstract class CheckApiUsage extends DefaultTask {

    private static final Pattern ENGINE_TYPE = Pattern.compile("dev/gulp/[A-Za-z0-9_$/-]+");

    /** Creates the task; instantiated by Gradle. */
    public CheckApiUsage() {}

    /**
     * Returns the compiled class folders of the game.
     *
     * @return the folders
     */
    @InputFiles
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getClassesDirs();

    /**
     * Returns the report written when the check passes, so the task is up to date until the classes change.
     *
     * @return the file
     */
    @OutputFile
    public abstract RegularFileProperty getReport();

    /** Scans the classes. */
    @TaskAction
    public void check() {
        List<Path> classes = new ArrayList<>();
        for (File directory : getClassesDirs().getFiles()) {
            if (!directory.isDirectory()) {
                continue;
            }
            try (var files = Files.walk(directory.toPath())) {
                files.filter(f -> f.toString().endsWith(".class")).forEach(classes::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        Set<String> own = new HashSet<>();
        List<byte[]> contents = new ArrayList<>();
        for (Path file : classes) {
            try {
                byte[] bytes = Files.readAllBytes(file);
                contents.add(bytes);
                own.add(className(bytes));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < classes.size(); i++) {
            String name = className(contents.get(i));
            for (String used : forbidden(contents.get(i), own)) {
                problems.add(name.replace('/', '.') + " uses " + used.replace('/', '.'));
            }
        }
        if (!problems.isEmpty()) {
            throw new GradleException(
                    "Game code may use dev.gulp.api only (section 6); found:\n  " + String.join("\n  ", problems));
        }
        try {
            File report = getReport().get().getAsFile();
            Files.createDirectories(report.getParentFile().toPath());
            Files.writeString(report.toPath(), classes.size() + " classes use dev.gulp.api only\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the engine types outside {@code dev.gulp.api} that a class refers to.
     *
     * @param classFile the class file
     * @param own internal names of the game's classes, which are allowed
     * @return internal names, sorted
     */
    static Set<String> forbidden(byte[] classFile, Set<String> own) {
        Set<String> ownPackages = new HashSet<>();
        for (String name : own) {
            ownPackages.add(name.substring(0, Math.max(0, name.lastIndexOf('/'))));
        }
        Set<String> found = new TreeSet<>();
        for (String text : utf8Constants(classFile)) {
            Matcher matcher = ENGINE_TYPE.matcher(text);
            while (matcher.find()) {
                String type = matcher.group();
                int nested = type.indexOf('$');
                String outer = nested < 0 ? type : type.substring(0, nested);
                String packageName = outer.substring(0, Math.max(0, outer.lastIndexOf('/')));
                if (!type.startsWith("dev/gulp/api/") && !own.contains(outer) && !ownPackages.contains(packageName)) {
                    found.add(type);
                }
            }
        }
        return found;
    }

    /**
     * Returns the internal name of a class file.
     *
     * @param classFile the bytes
     * @return such as {@code com/example/Game}
     */
    static String className(byte[] classFile) {
        ConstantPool pool = ConstantPool.read(classFile);
        return pool.utf8[pool.classNames[pool.thisClass]];
    }

    /**
     * Returns every UTF-8 constant: class names, descriptors and signatures, and also string literals.
     *
     * @param classFile the bytes
     * @return the texts
     */
    static List<String> utf8Constants(byte[] classFile) {
        ConstantPool pool = ConstantPool.read(classFile);
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < pool.utf8.length; i++) {
            // String literals are data, not references: "dev/gulp/core" in a message is not a use.
            if (pool.utf8[i] != null && !pool.literal[i]) {
                texts.add(pool.utf8[i]);
            }
        }
        return texts;
    }

    /** The parts of a class file's constant pool the check needs. */
    private static final class ConstantPool {
        String[] utf8;
        int[] classNames;
        boolean[] literal;
        int thisClass;

        static ConstantPool read(byte[] bytes) {
            try (DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(bytes))) {
                if (in.readInt() != 0xCAFEBABE) {
                    throw new GradleException("Not a class file");
                }
                in.readUnsignedShort();
                in.readUnsignedShort();
                int count = in.readUnsignedShort();
                ConstantPool pool = new ConstantPool();
                pool.utf8 = new String[count];
                pool.classNames = new int[count];
                pool.literal = new boolean[count];
                List<Integer> strings = new ArrayList<>();
                for (int i = 1; i < count; i++) {
                    int tag = in.readUnsignedByte();
                    switch (tag) {
                        case 1 -> pool.utf8[i] = in.readUTF();
                        case 7 -> pool.classNames[i] = in.readUnsignedShort();
                        case 8 -> strings.add(in.readUnsignedShort());
                        case 16, 19, 20 -> in.readUnsignedShort();
                        case 15 -> {
                            in.readUnsignedByte();
                            in.readUnsignedShort();
                        }
                        case 3, 4, 9, 10, 11, 12, 17, 18 -> in.readInt();
                        case 5, 6 -> {
                            in.readLong();
                            i++;
                        }
                        default -> throw new GradleException("Unknown constant pool tag " + tag);
                    }
                }
                for (int index : strings) {
                    pool.literal[index] = true;
                }
                in.readUnsignedShort();
                pool.thisClass = in.readUnsignedShort();
                return pool;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
