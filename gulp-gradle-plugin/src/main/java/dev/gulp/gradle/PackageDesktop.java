package dev.gulp.gradle;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

/**
 * Packages the game for the current system: {@code jdeps} finds the Java modules it needs, {@code jlink} builds a
 * small runtime with them, and {@code jpackage} wraps game, libraries and runtime into an installer (MSI or EXE on
 * Windows, DMG or PKG on macOS, DEB or RPM on Linux) or a plain application folder ({@code app-image}), which is then
 * zipped. The tools come from the Java toolchain of the build. Natives of other systems are left out.
 *
 * <pre>{@code
 * ./gradlew packageDesktop
 * # build/distributions/desktop/Coin Hunter-1.0.0.msi, or Coin Hunter-windows.zip without WiX
 * }</pre>
 */
public abstract class PackageDesktop extends DefaultTask {

    /** Modules every game needs even when {@code jdeps} misses them: LWJGL uses {@code sun.misc.Unsafe}. */
    static final List<String> BASE_MODULES = List.of("java.base", "java.logging", "java.net.http", "jdk.unsupported");

    /** Creates the task; instantiated by Gradle. */
    public PackageDesktop() {}

    /**
     * Returns the jar of the game.
     *
     * @return the jar
     */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getGameJar();

    /**
     * Returns the libraries: engine, desktop backend, LWJGL and the game's own dependencies.
     *
     * @return the jars
     */
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getLibraries();

    /**
     * Returns the class with {@code main}.
     *
     * @return the class name
     */
    @Input
    public abstract Property<String> getMainClass();

    /**
     * Returns the application name shown by the system.
     *
     * @return the name
     */
    @Input
    public abstract Property<String> getAppName();

    /**
     * Returns the version of the application, numbers only, such as {@code 1.2.0}.
     *
     * @return the version
     */
    @Input
    public abstract Property<String> getAppVersion();

    /**
     * Returns the package type passed to {@code jpackage}.
     *
     * @return {@code app-image}, {@code msi}, {@code exe}, {@code dmg}, {@code pkg}, {@code deb} or {@code rpm}
     */
    @Input
    public abstract Property<String> getInstallerType();

    /**
     * Returns the vendor of the application.
     *
     * @return the vendor
     */
    @Input
    @Optional
    public abstract Property<String> getVendor();

    /**
     * Returns the icon: {@code .ico} on Windows, {@code .icns} on macOS, {@code .png} on Linux.
     *
     * @return the icon file
     */
    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getIcon();

    /**
     * Returns extra JVM options of the packaged game.
     *
     * @return the options
     */
    @Input
    public abstract ListProperty<String> getJavaOptions();

    /**
     * Returns the Java installation whose {@code jdeps}, {@code jlink} and {@code jpackage} are used.
     *
     * @return the JDK home
     */
    @Internal
    public abstract DirectoryProperty getJavaHome();

    /**
     * Returns the working folder.
     *
     * @return the folder
     */
    @Internal
    public abstract DirectoryProperty getWorkDirectory();

    /**
     * Returns where the package goes.
     *
     * @return the folder
     */
    @OutputDirectory
    public abstract DirectoryProperty getDestination();

    /**
     * Returns process execution.
     *
     * @return the service
     */
    @Inject
    protected abstract ExecOperations getExec();

    /**
     * Returns file operations.
     *
     * @return the service
     */
    @Inject
    protected abstract FileSystemOperations getFiles();

    /** Runs jdeps, jlink and jpackage. */
    @TaskAction
    public void run() {
        Path work = getWorkDirectory().get().getAsFile().toPath();
        Path input = work.resolve("input");
        Path runtime = work.resolve("runtime");
        Path destination = getDestination().get().getAsFile().toPath();
        String os = currentOs();
        getFiles().delete(spec -> spec.delete(work.toFile(), destination.toFile()));
        try {
            Files.createDirectories(input);
            Files.createDirectories(destination);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        File gameJar = getGameJar().get().getAsFile();
        List<File> jars = new ArrayList<>();
        jars.add(gameJar);
        for (File library : getLibraries().getFiles()) {
            if (library.getName().endsWith(".jar") && keepNatives(library.getName(), os)) {
                jars.add(library);
            }
        }
        getFiles().copy(spec -> {
            spec.from(jars);
            spec.into(input.toFile());
        });

        String modules = String.join(",", modules(jars));
        getLogger().lifecycle("Java modules: " + modules);
        exec(
                tool("jlink"),
                List.of(
                        "--add-modules",
                        modules,
                        "--strip-debug",
                        "--no-header-files",
                        "--no-man-pages",
                        "--compress=zip-6",
                        "--output",
                        runtime.toString()));

        String type = getInstallerType().get();
        List<String> args = new ArrayList<>(List.of(
                "--type", type,
                "--name", getAppName().get(),
                "--app-version", getAppVersion().get(),
                "--input", input.toString(),
                "--main-jar", gameJar.getName(),
                "--main-class", getMainClass().get(),
                "--runtime-image", runtime.toString(),
                "--dest", destination.toString()));
        List<String> options = new ArrayList<>(List.of(
                "--enable-native-access=ALL-UNNAMED",
                "--sun-misc-unsafe-memory-access=allow",
                "-Dgulp.development=false",
                "-Dstdout.encoding=UTF-8"));
        if (os.equals("macos")) {
            options.add("-XstartOnFirstThread");
        }
        options.addAll(getJavaOptions().get());
        for (String option : options) {
            args.add("--java-options");
            args.add(option);
        }
        if (getVendor().isPresent()) {
            args.add("--vendor");
            args.add(getVendor().get());
        }
        if (getIcon().isPresent()) {
            args.add("--icon");
            args.add(getIcon().get().getAsFile().getAbsolutePath());
        }
        if (os.equals("windows") && !type.equals("app-image")) {
            args.addAll(List.of("--win-menu", "--win-shortcut", "--win-dir-chooser"));
        }
        if (os.equals("linux") && !type.equals("app-image")) {
            args.addAll(List.of("--linux-shortcut"));
        }
        exec(tool("jpackage"), args);
        if (type.equals("app-image")) {
            zipImage(destination, getAppName().get() + "-" + os + ".zip");
        }
        getLogger().lifecycle("Packaged into " + destination);
    }

    private void zipImage(Path destination, String zipName) {
        Path zip = destination.resolve(zipName);
        try (var out = new java.util.zip.ZipOutputStream(Files.newOutputStream(zip));
                var files = Files.walk(destination)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(f -> !f.equals(zip))
                    .toList()) {
                String name = destination.relativize(file).toString().replace('\\', '/');
                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(name);
                out.putNextEntry(entry);
                Files.copy(file, out);
                out.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Finds the modules of the jars with {@code jdeps}, adding {@link #BASE_MODULES}. */
    private TreeSet<String> modules(List<File> jars) {
        TreeSet<String> modules = new TreeSet<>(BASE_MODULES);
        StringBuilder classPath = new StringBuilder();
        for (File jar : jars) {
            if (classPath.length() > 0) {
                classPath.append(File.pathSeparatorChar);
            }
            classPath.append(jar.getAbsolutePath());
        }
        List<String> args = new ArrayList<>(List.of(
                "--ignore-missing-deps",
                "--print-module-deps",
                "--multi-release",
                "base",
                "--class-path",
                classPath.toString()));
        for (File jar : jars) {
            args.add(jar.getAbsolutePath());
        }
        try {
            String output = exec(tool("jdeps"), args);
            modules.addAll(parseModules(output));
        } catch (GradleException e) {
            getLogger().warn("jdeps failed, using the base module set: " + e.getMessage());
        }
        return modules;
    }

    /**
     * Reads the module list {@code jdeps --print-module-deps} prints on its last line.
     *
     * @param output the output
     * @return the modules
     */
    static List<String> parseModules(String output) {
        String[] lines = output.trim().split("\\R");
        String last = lines[lines.length - 1].trim();
        if (last.isEmpty() || last.contains(" ")) {
            return List.of();
        }
        return Arrays.stream(last.split(","))
                .map(String::trim)
                .filter(m -> !m.isEmpty())
                .toList();
    }

    /**
     * Returns whether a jar belongs in the package of a system: natives of other systems are left out.
     *
     * @param name the jar name
     * @param os {@code windows}, {@code macos} or {@code linux}
     * @return whether to keep it
     */
    static boolean keepNatives(String name, String os) {
        int at = name.indexOf("-natives-");
        if (at < 0) {
            return true;
        }
        String rest = name.substring(at + "-natives-".length());
        return rest.startsWith(os + "-") || rest.startsWith(os + ".");
    }

    /**
     * Returns the current system as LWJGL names natives.
     *
     * @return {@code windows}, {@code macos} or {@code linux}
     */
    static String currentOs() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return name.contains("win") ? "windows" : name.contains("mac") ? "macos" : "linux";
    }

    /**
     * Chooses the package type of a system from the tools installed: MSI needs WiX on Windows, DEB needs
     * {@code dpkg-deb} and RPM {@code rpmbuild} on Linux; without them the game ships as a zipped folder.
     *
     * @param os the system
     * @param onPath whether a program is on the {@code PATH}
     * @return the type
     */
    static String defaultType(String os, java.util.function.Predicate<String> onPath) {
        return switch (os) {
            case "windows" -> onPath.test("wix") || onPath.test("candle") ? "msi" : "app-image";
            case "macos" -> "dmg";
            default -> onPath.test("dpkg-deb") ? "deb" : onPath.test("rpmbuild") ? "rpm" : "app-image";
        };
    }

    /**
     * Returns whether a program is on the {@code PATH}.
     *
     * @param program the name without extension
     * @return {@code true} if found
     */
    static boolean onPath(String program) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String directory : path.split(File.pathSeparator)) {
            for (String extension : List.of("", ".exe", ".cmd", ".bat")) {
                if (new File(directory, program + extension).isFile()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Turns a project version into one {@code jpackage} accepts: up to three numbers, the first above zero (macOS
     * refuses 0.x).
     *
     * @param version such as {@code 0.3.1-SNAPSHOT}
     * @return such as {@code 1.3.1}
     */
    static String appVersion(String version) {
        String numbers = version.replaceAll("[^0-9.].*$", "");
        List<String> parts = new ArrayList<>();
        for (String part : numbers.split("\\.")) {
            if (!part.isEmpty() && parts.size() < 3) {
                parts.add(part);
            }
        }
        if (parts.isEmpty() || Integer.parseInt(parts.get(0)) == 0) {
            if (parts.isEmpty()) {
                parts.add("1");
            } else {
                parts.set(0, "1");
            }
        }
        while (parts.size() < 3) {
            parts.add("0");
        }
        return String.join(".", parts);
    }

    private String tool(String name) {
        Path bin = getJavaHome().get().getAsFile().toPath().resolve("bin");
        Path exe = bin.resolve(name + (currentOs().equals("windows") ? ".exe" : ""));
        if (!Files.isRegularFile(exe)) {
            throw new GradleException(name + " is missing from " + bin + "; packaging needs a full JDK");
        }
        return exe.toString();
    }

    private String exec(String executable, List<String> args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        var result = getExec().exec(spec -> {
            spec.setExecutable(executable);
            spec.setArgs(args);
            spec.setStandardOutput(out);
            spec.setErrorOutput(out);
            spec.setIgnoreExitValue(true);
        });
        String text = out.toString(StandardCharsets.UTF_8);
        if (result.getExitValue() != 0) {
            throw new GradleException(
                    new File(executable).getName() + " failed (" + result.getExitValue() + "):\n" + text);
        }
        return text;
    }
}
