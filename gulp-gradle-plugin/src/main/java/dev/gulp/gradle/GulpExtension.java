package dev.gulp.gradle;

import org.gradle.api.provider.Property;

/**
 * The {@code gulp { ... }} block of a game build.
 *
 * <pre>{@code
 * gulp {
 *     mainClass = "com.example.coins.CoinGame"
 *     title = "Coin Hunter"
 * }
 * }</pre>
 *
 * <p>Desktop, web and asset settings and the tasks {@code runDesktop}, {@code runWeb} and {@code buildWeb} arrive in
 * stage 3.
 */
public abstract class GulpExtension {

    /** Creates the extension; instantiated by Gradle. */
    public GulpExtension() {}

    /**
     * Returns the fully qualified name of the game's {@code Game} subclass.
     *
     * @return the main class property
     */
    public abstract Property<String> getMainClass();

    /**
     * Returns the game title used for windows, web pages and installers.
     *
     * @return the title property
     */
    public abstract Property<String> getTitle();
}
