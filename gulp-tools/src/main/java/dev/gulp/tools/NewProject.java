package dev.gulp.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Creates a game project from {@code templates/game}: renames the package, the game class, its id and title, adds the
 * Gradle wrapper and points {@code gulp.home} at the Gulp checkout the project builds against.
 *
 * <pre>{@code
 * NewProject.create(Path.of("."), Path.of("../coin-hunter"), "com.example.coins", "Coin Hunter");
 * // ../coin-hunter: ./gradlew runDesktop, runWeb, packageDesktop, packageWeb
 * }</pre>
 */
public final class NewProject {

    private static final Pattern PACKAGE = Pattern.compile("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*");
    private static final String TEMPLATE_PACKAGE = "com.example.mygame";

    private NewProject() {}

    /**
     * Creates the project.
     *
     * @param gulpHome the Gulp checkout, holding {@code templates/game} and the wrapper
     * @param target the new project folder, which must not exist or be empty
     * @param packageName the Java package of the game, such as {@code com.example.coins}
     * @param title the window title, such as {@code Coin Hunter}; the class and id derive from it
     * @return the main class of the new project
     * @throws IOException if files cannot be copied
     * @throws IllegalArgumentException if the package or title are not usable or the target is not empty
     */
    public static String create(Path gulpHome, Path target, String packageName, String title) throws IOException {
        if (!PACKAGE.matcher(packageName).matches()) {
            throw new IllegalArgumentException("Not a Java package name: " + packageName);
        }
        String className = className(title);
        String id = className.toLowerCase(Locale.ROOT);
        Path template = gulpHome.resolve("templates/game");
        if (!Files.isDirectory(template)) {
            throw new IllegalArgumentException("No template at " + template.toAbsolutePath());
        }
        if (Files.isDirectory(target)) {
            try (Stream<Path> files = Files.list(target)) {
                if (files.findAny().isPresent()) {
                    throw new IllegalArgumentException("The folder is not empty: " + target.toAbsolutePath());
                }
            }
        }
        String packagePath = packageName.replace('.', '/');
        List<Path> sources;
        try (Stream<Path> files = Files.walk(template)) {
            sources = files.filter(Files::isRegularFile)
                    .filter(f -> !template.relativize(f).startsWith("build"))
                    .filter(f -> !template.relativize(f).startsWith(".gradle"))
                    .toList();
        }
        for (Path source : sources) {
            String relative = template.relativize(source).toString().replace('\\', '/');
            relative = relative.replace(TEMPLATE_PACKAGE.replace('.', '/'), packagePath)
                    .replace("assets/mygame/", "assets/" + id + "/")
                    .replace("MyGame", className);
            if (relative.equals("gitignore.txt")) {
                relative = ".gitignore";
            }
            Path destination = target.resolve(relative);
            Files.createDirectories(destination.getParent());
            String text = Files.readString(source, StandardCharsets.UTF_8)
                    .replace(TEMPLATE_PACKAGE, packageName)
                    .replace("MyGame", className)
                    .replace("My Game", title)
                    .replace("\"mygame\"", "\"" + id + "\"")
                    .replace("mygame:", id + ":")
                    .replace("group = \"com.example\"", "group = \"" + group(packageName) + "\"");
            Files.writeString(destination, text, StandardCharsets.UTF_8);
        }
        for (String wrapper : List.of(
                "gradlew",
                "gradlew.bat",
                "gradle/wrapper/gradle-wrapper.jar",
                "gradle/wrapper/gradle-wrapper.properties")) {
            Path source = gulpHome.resolve(wrapper);
            if (Files.isRegularFile(source)) {
                Path destination = target.resolve(wrapper);
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                if (wrapper.equals("gradlew")) {
                    destination.toFile().setExecutable(true);
                }
            }
        }
        String home = gulpHome.toAbsolutePath().normalize().toString().replace('\\', '/');
        Path properties = target.resolve("gradle.properties");
        Files.writeString(
                properties,
                Files.readString(properties, StandardCharsets.UTF_8)
                        + "\n# The Gulp checkout this game builds against.\n" + "gulp.home=" + home + "\n",
                StandardCharsets.UTF_8);
        return packageName + "." + className;
    }

    /**
     * Turns a title into a class name.
     *
     * @param title such as {@code Coin Hunter 2}
     * @return such as {@code CoinHunter2}
     * @throws IllegalArgumentException if nothing usable is left
     */
    static String className(String title) {
        StringBuilder out = new StringBuilder();
        boolean upper = true;
        for (char c : title.toCharArray()) {
            if (c < 128 && Character.isLetterOrDigit(c)) {
                out.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            } else {
                upper = true;
            }
        }
        if (out.isEmpty() || !Character.isLetter(out.charAt(0))) {
            throw new IllegalArgumentException("The title needs to start with a Latin letter: " + title);
        }
        return out.toString();
    }

    private static String group(String packageName) {
        int dot = packageName.lastIndexOf('.');
        return dot < 0 ? packageName : packageName.substring(0, dot);
    }
}
