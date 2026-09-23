package dev.gulp.backend.desktop;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * GLFW on macOS must run on the process's first thread, which the JVM only uses for {@code main} when started with
 * {@code -XstartOnFirstThread}. If that flag is missing, this restarts the same command with it and exits with the
 * child's exit code, so {@code ./gradlew run} and IDE run configurations work without extra setup.
 */
final class MacOsFirstThread {

    /** Set in the child's environment to prevent an endless restart loop. */
    static final String RESTARTED_ENV = "GULP_RESTARTED_ON_FIRST_THREAD";

    private MacOsFirstThread() {}

    static void relaunchIfNeeded() {
        if (!isMacOs(System.getProperty("os.name", ""))) {
            return;
        }
        long pid = ProcessHandle.current().pid();
        if ("1".equals(System.getenv("JAVA_STARTED_ON_FIRST_THREAD_" + pid))) {
            return;
        }
        if (System.getenv(RESTARTED_ENV) != null) {
            System.err.println("[gulp] Restarted with -XstartOnFirstThread but still not on the first thread;"
                    + " continuing, GLFW will probably fail.");
            return;
        }
        List<String> command = restartCommand(
                javaExecutable(),
                ManagementFactory.getRuntimeMXBean().getInputArguments(),
                System.getProperty("java.class.path", ""),
                System.getProperty("sun.java.command", ""));
        try {
            ProcessBuilder builder = new ProcessBuilder(command).inheritIO();
            builder.environment().put(RESTARTED_ENV, "1");
            int exitCode = builder.start().waitFor();
            System.exit(exitCode);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not restart the JVM with -XstartOnFirstThread; add it to the JVM arguments", e);
        }
    }

    static boolean isMacOs(String osName) {
        String name = osName.toLowerCase(Locale.ROOT);
        return name.startsWith("mac") || name.startsWith("darwin");
    }

    /**
     * Builds the command that restarts this JVM with {@code -XstartOnFirstThread}.
     *
     * @param javaCommand path to the {@code java} executable
     * @param jvmArguments arguments of the running JVM
     * @param classPath the class path
     * @param mainCommand the value of {@code sun.java.command}: main class or {@code jar} path, then program arguments
     *     separated by spaces (arguments containing spaces are split; a known limitation)
     * @return the new command line
     */
    static List<String> restartCommand(
            String javaCommand, List<String> jvmArguments, String classPath, String mainCommand) {
        List<String> command = new ArrayList<>();
        command.add(javaCommand);
        command.add("-XstartOnFirstThread");
        command.addAll(jvmArguments);
        String[] main = mainCommand.trim().split(" +");
        boolean jar = main.length > 0 && main[0].endsWith(".jar");
        if (!jar && !classPath.isEmpty()) {
            command.add("-cp");
            command.add(classPath);
        }
        if (jar) {
            command.add("-jar");
        }
        for (String part : main) {
            if (!part.isEmpty()) {
                command.add(part);
            }
        }
        return command;
    }

    private static String javaExecutable() {
        return ProcessHandle.current()
                .info()
                .command()
                .orElse(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    }
}
