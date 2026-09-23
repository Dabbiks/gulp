package dev.gulp.platform;

/**
 * One complete platform implementation. A backend creates it after reading the game's settings and hands it to
 * {@code gulp-core}.
 *
 * <pre>{@code
 * PlatformBackend backend = DesktopBackend.create(windowConfig);
 * try {
 *     runtime.run(backend);
 * } finally {
 *     backend.dispose();
 * }
 * }</pre>
 */
public interface PlatformBackend {

    /**
     * Returns the backend name, the same as its launcher name.
     *
     * @return for example {@code "desktop"}, {@code "web"} or {@code "headless"}
     */
    String name();

    /**
     * Returns what drives frames.
     *
     * @return the frame loop
     */
    PlatformLoop loop();

    /**
     * Returns the OpenGL ES 3.0 / WebGL2 abstraction.
     *
     * @return the graphics context, current on the main thread
     */
    Gl gl();

    /**
     * Returns the window, or the canvas on the web.
     *
     * @return the window
     */
    PlatformWindow window();

    /**
     * Returns raw input: keyboard, text, mouse, touch, gamepads and clipboard.
     *
     * @return the input source
     */
    PlatformInput input();

    /**
     * Returns PCM playback.
     *
     * @return the audio device
     */
    PlatformAudio audio();

    /**
     * Returns read-only assets and writable user data.
     *
     * @return the file system
     */
    PlatformFiles files();

    /**
     * Returns image, audio and font decoders.
     *
     * @return the decoders
     */
    PlatformDecoders decoders();

    /**
     * Returns the executor for work outside the main tick.
     *
     * @return the executor
     */
    PlatformExecutor executor();

    /**
     * Returns HTTP and WebSocket access.
     *
     * @return the network access
     */
    PlatformNet net();

    /**
     * Returns read-only information about the system.
     *
     * @return system information
     */
    PlatformInfo info();

    /**
     * Returns the registry of code generated at compile time.
     *
     * @return generated modules
     */
    PlatformModules modules();

    /** Releases the window, graphics context, audio device and threads. Called once, after the loop exits. */
    void dispose();
}
