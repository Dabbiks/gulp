package dev.gulp.api;

/**
 * Read-only facts about the platform the game runs on.
 *
 * <pre>{@code
 * if (engine().platform().isWeb()) showClickToStart();
 * }</pre>
 */
public interface Platform {

    /**
     * Returns the backend name.
     *
     * @return {@code "desktop"}, {@code "web"} or {@code "headless"}
     */
    String backend();

    /**
     * Returns the operating system, or the browser on the web.
     *
     * @return for example {@code "Windows 11 10.0"}
     */
    String osName();

    /**
     * Returns the CPU architecture.
     *
     * @return for example {@code "x64"}, {@code "arm64"} or {@code "wasm"}
     */
    String architecture();

    /**
     * Returns whether the game runs in a browser.
     *
     * @return {@code true} on the web
     */
    boolean isWeb();

    /**
     * Returns whether this is a development build (console, inspectors, hot reload).
     *
     * @return {@code true} in development builds
     */
    boolean isDevelopment();

    /**
     * Returns the system language.
     *
     * @return a BCP 47 tag such as {@code "pl-PL"}
     */
    String systemLocale();
}
