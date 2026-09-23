package dev.gulp.platform;

/**
 * Read-only facts about the system, for adapting behaviour and for crash reports.
 *
 * <pre>{@code
 * if (info.isWeb()) showClickToStart();
 * }</pre>
 */
public interface PlatformInfo {

    /**
     * Returns the operating system.
     *
     * @return for example {@code "Windows 11"}, {@code "macOS 15.4"}, or the browser name on the web
     */
    String osName();

    /**
     * Returns the CPU architecture.
     *
     * @return for example {@code "x64"} or {@code "arm64"}; {@code "wasm"} on the web
     */
    String architecture();

    /**
     * Returns whether this is the web backend.
     *
     * @return {@code true} in a browser
     */
    boolean isWeb();

    /**
     * Returns whether this is a development build (console, inspectors and hot reload allowed).
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

    /**
     * Returns the primary screen width.
     *
     * @return width in logical points
     */
    int screenWidth();

    /**
     * Returns the primary screen height.
     *
     * @return height in logical points
     */
    int screenHeight();

    /**
     * Returns the graphics adapter description.
     *
     * @return renderer and version, for example {@code "NVIDIA GeForce RTX 4070 / OpenGL 3.3"}
     */
    String gpuDescription();
}
