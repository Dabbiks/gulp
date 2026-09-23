package dev.gulp.backend.web;

import dev.gulp.api.data.JsonObject;
import dev.gulp.api.data.JsonValue;
import dev.gulp.core.GeneratedModules;
import dev.gulp.core.MainQueue;
import dev.gulp.core.data.JsonReader;
import dev.gulp.platform.CursorMode;
import dev.gulp.platform.DecodedAudio;
import dev.gulp.platform.DecodedImage;
import dev.gulp.platform.FrameHandler;
import dev.gulp.platform.Gl;
import dev.gulp.platform.InputListener;
import dev.gulp.platform.PlatformAudio;
import dev.gulp.platform.PlatformBackend;
import dev.gulp.platform.PlatformCallback;
import dev.gulp.platform.PlatformConsole;
import dev.gulp.platform.PlatformDecoders;
import dev.gulp.platform.PlatformExecutor;
import dev.gulp.platform.PlatformFiles;
import dev.gulp.platform.PlatformFontFace;
import dev.gulp.platform.PlatformInfo;
import dev.gulp.platform.PlatformInput;
import dev.gulp.platform.PlatformLog;
import dev.gulp.platform.PlatformLoop;
import dev.gulp.platform.PlatformNet;
import dev.gulp.platform.PlatformWindow;
import dev.gulp.platform.ResourcePackInfo;
import dev.gulp.platform.WindowListener;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.Int8Array;
import org.teavm.jso.webgl.WebGL2RenderingContext;

/**
 * Web backend: a canvas with WebGL2, frames from {@code requestAnimationFrame}, files through {@code fetch}, user data
 * in IndexedDB, images decoded by the browser. Browser callbacks are queued and delivered at the start of the next
 * frame, so the engine only ever runs inside a frame.
 *
 * <p>The page must contain {@code <canvas id="gulp-canvas">} and load {@code gulp-runtime.js}; the template from the
 * Gradle plugin does both.
 */
public final class WebBackend implements PlatformBackend {

    /** Time a frame may spend on cooperative background tasks. */
    private static final double EXECUTOR_BUDGET_MILLIS = 4;

    private final MainQueue mainQueue = new MainQueue();
    private final WebGl gl;
    private final Window window = new Window();
    private final Loop loop = new Loop();
    private final Input input = new Input();
    private final Files files = new Files();
    private final Decoders decoders = new Decoders();
    private final Executor executor = new Executor();
    private final Log log = new Log();
    private final Console console = new Console();
    private final Info info;

    private WebBackend(WebGL2RenderingContext context) {
        this.gl = new WebGl(context);
        this.info = new Info(gl);
    }

    /**
     * Sets up the canvas and the WebGL2 context.
     *
     * @param appId the game id, used as the IndexedDB database name
     * @param title the page title
     * @return the backend
     * @throws IllegalStateException if the browser has no WebGL2
     */
    public static WebBackend create(String appId, String title) {
        WebGL2RenderingContext context = Js.init();
        if (context == null) {
            throw new IllegalStateException("This browser does not support WebGL2, which Gulp games need.");
        }
        Js.setDatabaseName("gulp-" + appId);
        Js.setTitle(title);
        WebBackend backend = new WebBackend(context);
        backend.window.install();
        backend.input.install();
        backend.files.loadManifest();
        return backend;
    }

    @Override
    public String name() {
        return WebGameLauncher.NAME;
    }

    @Override
    public PlatformLoop loop() {
        return loop;
    }

    @Override
    public Gl gl() {
        return gl;
    }

    @Override
    public PlatformWindow window() {
        return window;
    }

    @Override
    public PlatformInput input() {
        return input;
    }

    @Override
    public PlatformAudio audio() {
        throw new UnsupportedOperationException("PlatformAudio is not implemented on the web yet (roadmap stage 5)");
    }

    @Override
    public PlatformFiles files() {
        return files;
    }

    @Override
    public PlatformDecoders decoders() {
        return decoders;
    }

    @Override
    public PlatformExecutor executor() {
        return executor;
    }

    @Override
    public PlatformNet net() {
        throw new UnsupportedOperationException("PlatformNet is not implemented on the web yet (roadmap stage 10)");
    }

    @Override
    public PlatformInfo info() {
        return info;
    }

    @Override
    public GeneratedModules modules() {
        return GeneratedModules.shared();
    }

    @Override
    public PlatformLog log() {
        return log;
    }

    @Override
    public PlatformConsole console() {
        return console;
    }

    @Override
    public void dispose() {
        executor.shutdown();
    }

    private void beforeFrame() {
        mainQueue.drain(
                error -> log.write(PlatformLog.ERROR, "gulp", "Unhandled exception in a browser callback", error));
        executor.runFor(EXECUTOR_BUDGET_MILLIS);
    }

    private static ByteBuffer toBuffer(ArrayBuffer bytes) {
        byte[] array = Js.bytes(bytes).copyToJavaArray();
        ByteBuffer buffer = ByteBuffer.allocateDirect(array.length).order(ByteOrder.nativeOrder());
        buffer.put(array).flip();
        return buffer;
    }

    private static Int8Array toJs(ByteBuffer data) {
        byte[] array = new byte[data.remaining()];
        data.duplicate().get(array);
        return Int8Array.copyFromJavaArray(array);
    }

    // ------------------------------------------------------------------ loop

    private final class Loop implements PlatformLoop {
        private @Nullable FrameHandler handler;
        private boolean running;
        private boolean firstFrame = true;
        private final Js.FrameCallback callback = this::frame;

        @Override
        public void run(FrameHandler frameHandler) {
            if (running) {
                throw new IllegalStateException("The web loop is already running");
            }
            handler = frameHandler;
            running = true;
            Js.requestAnimationFrame(callback);
        }

        private void frame(double timestamp) {
            FrameHandler current = handler;
            if (!running || current == null) {
                return;
            }
            boolean keepRunning;
            try {
                beforeFrame();
                keepRunning = current.frame((long) (Js.now() * 1_000_000.0));
            } catch (Throwable error) {
                log.write(PlatformLog.ERROR, "gulp", "The frame failed", error);
                Js.fatal("The game crashed: " + error);
                keepRunning = false;
            }
            if (firstFrame) {
                firstFrame = false;
                Js.ready();
            }
            if (keepRunning && running) {
                Js.requestAnimationFrame(callback);
            } else {
                running = false;
                handler = null;
                current.exit();
                dispose();
            }
        }

        @Override
        public void stop() {
            running = false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }
    }

    // ------------------------------------------------------------------ window

    private final class Window implements PlatformWindow {
        private @Nullable WindowListener listener;
        private boolean focused = true;
        private boolean closeRequested;

        void install() {
            Js.onResize(() -> {
                WindowListener current = listener;
                if (current != null) {
                    current.resized(width(), height(), framebufferWidth(), framebufferHeight());
                }
            });
            Js.onFocus(value -> {
                focused = value;
                WindowListener current = listener;
                if (current != null) {
                    current.focusChanged(value);
                }
            });
        }

        @Override
        public int width() {
            return Js.width();
        }

        @Override
        public int height() {
            return Js.height();
        }

        @Override
        public int framebufferWidth() {
            return Js.framebufferWidth();
        }

        @Override
        public int framebufferHeight() {
            return Js.framebufferHeight();
        }

        @Override
        public float contentScale() {
            return (float) Js.devicePixelRatio();
        }

        @Override
        public void setTitle(String title) {
            Js.setTitle(title);
        }

        @Override
        public void setIcon(DecodedImage icon) {
            // The page's favicon comes from the HTML template.
        }

        @Override
        public void setSize(int width, int height) {
            // The canvas follows its CSS size; the page decides it.
        }

        @Override
        public boolean isFullscreen() {
            return Js.isFullscreen();
        }

        @Override
        public void setFullscreen(boolean fullscreen) {
            Js.setFullscreen(fullscreen);
        }

        @Override
        public void setVsync(boolean vsync) {
            // requestAnimationFrame always follows the display.
        }

        @Override
        public void setCursorMode(CursorMode mode) {
            Js.setCursor(mode.ordinal());
        }

        @Override
        public boolean isFocused() {
            return focused;
        }

        @Override
        public boolean shouldClose() {
            return closeRequested;
        }

        @Override
        public void requestClose() {
            closeRequested = true;
        }

        @Override
        public void setListener(@Nullable WindowListener listener) {
            this.listener = listener;
        }
    }

    // ------------------------------------------------------------------ input

    private final class Input implements PlatformInput {
        private @Nullable InputListener listener;
        private float lastX;
        private float lastY;

        void install() {
            Js.input(
                    (keyCode, scanCode, modifiers, down, repeat) -> post(l -> {
                        if (down) {
                            l.keyDown(keyCode, scanCode, modifiers, repeat);
                        } else {
                            l.keyUp(keyCode, scanCode, modifiers);
                        }
                    }),
                    codePoint -> post(l -> l.textTyped(codePoint)),
                    (x, y, dx, dy) -> {
                        lastX = (float) x;
                        lastY = (float) y;
                        post(l -> l.mouseMoved((float) x, (float) y, (float) dx, (float) dy));
                    },
                    (button, down, modifiers) -> post(l -> l.mouseButton(button, down, modifiers)),
                    (dx, dy) -> post(l -> l.scrolled((float) dx, (float) dy)),
                    (pointer, phase, x, y) -> post(l -> l.touch(pointer, phase, (float) x, (float) y)));
        }

        private void post(Consumer<InputListener> event) {
            if (listener != null) {
                mainQueue.post(() -> {
                    InputListener current = listener;
                    if (current != null) {
                        event.accept(current);
                    }
                });
            }
        }

        float lastX() {
            return lastX;
        }

        float lastY() {
            return lastY;
        }

        @Override
        public void setListener(@Nullable InputListener listener) {
            this.listener = listener;
        }

        @Override
        public void pollGamepads() {
            // Gamepads arrive in stage 5.
        }

        @Override
        public boolean isGamepadConnected(int index) {
            return false;
        }

        @Override
        public @Nullable String gamepadName(int index) {
            return null;
        }

        @Override
        public float gamepadAxis(int index, int axis) {
            return 0f;
        }

        @Override
        public boolean gamepadButton(int index, int button) {
            return false;
        }

        @Override
        public boolean rumble(int index, float weak, float strong, int durationMillis) {
            return false;
        }

        @Override
        public void readClipboard(PlatformCallback<String> callback) {
            mainQueue.post(() -> callback.failure(new UnsupportedOperationException("Clipboard arrives in stage 5")));
        }

        @Override
        public void writeClipboard(String text) {
            // Clipboard arrives in stage 5.
        }
    }

    // ------------------------------------------------------------------ files

    /**
     * Assets come from {@code fetch}. The asset manifest is read first, so asking for a file that does not exist (an
     * optional config, for example) answers at once instead of logging a failed request in the browser console.
     */
    private final class Files implements PlatformFiles {
        private @Nullable Set<String> manifest;
        private boolean manifestLoaded;
        private @Nullable List<Runnable> waiting = new ArrayList<>();

        void loadManifest() {
            Js.loadManifest(
                    joined -> mainQueue.post(() -> manifestArrived(new HashSet<>(Arrays.asList(joined.split("\n"))))),
                    () -> mainQueue.post(() -> manifestArrived(null)));
        }

        private void manifestArrived(@Nullable Set<String> paths) {
            manifest = paths;
            manifestLoaded = true;
            List<Runnable> pending = waiting;
            waiting = null;
            if (pending != null) {
                pending.forEach(Runnable::run);
            }
        }

        @Override
        public void readAsset(String path, PlatformCallback<ByteBuffer> callback) {
            if (!manifestLoaded) {
                Objects.requireNonNull(waiting).add(() -> readAsset(path, callback));
                return;
            }
            Set<String> known = manifest;
            if (known != null && !known.contains(path) && !path.equals("assets.manifest.json")) {
                mainQueue.post(() -> callback.failure(new FileNotFoundException("No asset '" + path + "'")));
                return;
            }
            Js.fetchBytes(
                    "assets/" + path,
                    bytes -> mainQueue.post(() -> callback.success(toBuffer(bytes))),
                    () -> mainQueue.post(() -> callback.failure(new FileNotFoundException("No asset '" + path + "'"))),
                    message -> mainQueue.post(
                            () -> callback.failure(new IOException("Cannot read asset '" + path + "': " + message))));
        }

        @Override
        public void readUserData(String name, PlatformCallback<ByteBuffer> callback) {
            Js.readData(
                    name,
                    bytes -> mainQueue.post(() -> callback.success(toBuffer(bytes))),
                    () -> mainQueue.post(
                            () -> callback.failure(new FileNotFoundException("No user data '" + name + "'"))),
                    message -> mainQueue.post(() -> callback.failure(new IllegalStateException(message))));
        }

        @Override
        public void writeUserData(String name, ByteBuffer data, PlatformCallback<Void> callback) {
            Js.writeData(
                    name,
                    toJs(data),
                    () -> mainQueue.post(() -> callback.success(null)),
                    message -> mainQueue.post(() -> callback.failure(new IllegalStateException(message))));
        }

        @Override
        public void deleteUserData(String name, PlatformCallback<Void> callback) {
            Js.deleteData(
                    name,
                    () -> mainQueue.post(() -> callback.success(null)),
                    message -> mainQueue.post(() -> callback.failure(new IllegalStateException(message))));
        }

        @Override
        public void listUserData(String prefix, PlatformCallback<List<String>> callback) {
            Js.listData(
                    prefix,
                    joined -> mainQueue.post(() -> {
                        List<String> names = new ArrayList<>();
                        for (String name : joined.split("\n")) {
                            if (!name.isEmpty()) {
                                names.add(name);
                            }
                        }
                        callback.success(names);
                    }),
                    message -> mainQueue.post(() -> callback.failure(new IllegalStateException(message))));
        }

        /** Packs bundled by the build are listed in {@code resourcepacks/index.json}. */
        @Override
        public void listResourcePacks(PlatformCallback<List<ResourcePackInfo>> callback) {
            Js.fetchBytes(
                    "resourcepacks/index.json",
                    bytes -> mainQueue.post(() -> {
                        List<ResourcePackInfo> packs = new ArrayList<>();
                        try {
                            String text = new String(Js.bytes(bytes).copyToJavaArray(), StandardCharsets.UTF_8);
                            for (JsonValue value : JsonReader.parse(text)
                                    .asObject()
                                    .getOrThrow("packs")
                                    .asArray()) {
                                JsonObject pack = value.asObject();
                                List<String> files = new ArrayList<>();
                                for (JsonValue file : pack.getOrThrow("files").asArray()) {
                                    files.add(file.asString());
                                }
                                JsonValue description = pack.get("description");
                                packs.add(new ResourcePackInfo(
                                        pack.getOrThrow("id").asString(),
                                        description == null ? "" : description.asString(),
                                        files));
                            }
                        } catch (RuntimeException e) {
                            callback.failure(e);
                            return;
                        }
                        callback.success(packs);
                    }),
                    () -> mainQueue.post(() -> callback.success(List.of())),
                    message -> mainQueue.post(() -> callback.success(List.of())));
        }

        @Override
        public void readResourcePackFile(String packId, String path, PlatformCallback<ByteBuffer> callback) {
            Js.fetchBytes(
                    "resourcepacks/" + packId + "/" + path,
                    bytes -> mainQueue.post(() -> callback.success(toBuffer(bytes))),
                    () -> mainQueue.post(() -> callback.failure(
                            new FileNotFoundException("No '" + path + "' in resource pack '" + packId + "'"))),
                    message -> mainQueue.post(() -> callback.failure(new IOException(message))));
        }

        @Override
        public void watchAssets(@Nullable Consumer<String> listener) {
            // The runWeb server reloads the whole page after a rebuild instead.
        }

        @Override
        public String userDataLocation() {
            return "IndexedDB";
        }
    }

    // ------------------------------------------------------------------ decoders

    private final class Decoders implements PlatformDecoders {
        @Override
        public void decodeImage(ByteBuffer encoded, PlatformCallback<DecodedImage> callback) {
            if (!encoded.hasRemaining()) {
                mainQueue.post(() -> callback.failure(new IllegalArgumentException("Empty image data")));
                return;
            }
            Js.decodeImage(
                    toJs(encoded),
                    (width, height, rgba) -> {
                        byte[] pixels = rgba.copyToJavaArray();
                        ByteBuffer buffer =
                                ByteBuffer.allocateDirect(pixels.length).order(ByteOrder.nativeOrder());
                        buffer.put(pixels).flip();
                        DecodedImage image = new DecodedImage(width, height, buffer);
                        mainQueue.post(() -> callback.success(image));
                    },
                    message -> mainQueue.post(
                            () -> callback.failure(new IllegalArgumentException("Cannot decode image: " + message))));
        }

        @Override
        public void decodeAudio(ByteBuffer encoded, PlatformCallback<DecodedAudio> callback) {
            mainQueue.post(
                    () -> callback.failure(new UnsupportedOperationException("Audio decoding arrives in stage 5")));
        }

        @Override
        public void openFont(ByteBuffer fontFile, PlatformCallback<PlatformFontFace> callback) {
            Js.openFont(
                    toJs(fontFile),
                    family -> mainQueue.post(() -> callback.success(new WebFontFace(family))),
                    message -> mainQueue.post(
                            () -> callback.failure(new IllegalArgumentException("Cannot load font: " + message))));
        }
    }

    // ------------------------------------------------------------------ executor

    /** Runs background tasks cooperatively between frames: the browser main thread is the only one. */
    private static final class Executor implements PlatformExecutor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        private boolean shutDown;

        @Override
        public void execute(Runnable task) {
            if (shutDown) {
                throw new IllegalStateException("The executor is shut down");
            }
            tasks.add(task);
        }

        void runFor(double budgetMillis) {
            double end = Js.now() + budgetMillis;
            while (!tasks.isEmpty()) {
                tasks.poll().run();
                if (Js.now() >= end) {
                    return;
                }
            }
        }

        @Override
        public boolean isConcurrent() {
            return false;
        }

        @Override
        public void shutdown() {
            shutDown = true;
            tasks.clear();
        }
    }

    // ------------------------------------------------------------------ log, console, info

    private static final class Log implements PlatformLog {
        private static final String[] LEVELS = {"TRACE", "DEBUG", "INFO", "WARN", "ERROR"};

        @Override
        public void write(int level, String logger, String message, @Nullable Throwable error) {
            String line = "[" + LEVELS[Math.max(0, Math.min(LEVELS.length - 1, level))] + "] [" + logger + "] "
                    + message + (error == null ? "" : "\n" + describe(error));
            if (level >= ERROR) {
                Js.error(line);
            } else if (level == WARN) {
                Js.warn(line);
            } else {
                Js.log(line);
            }
        }

        private static String describe(Throwable error) {
            StringBuilder text = new StringBuilder(error.toString());
            for (StackTraceElement element : error.getStackTrace()) {
                text.append("\n    at ").append(element);
            }
            Throwable cause = error.getCause();
            if (cause != null && cause != error) {
                text.append("\nCaused by: ").append(describe(cause));
            }
            return text.toString();
        }
    }

    /** Console commands come from the browser's developer tools: {@code gulp.command("/tps")}. */
    private final class Console implements PlatformConsole {
        @Override
        public void setInputListener(@Nullable Consumer<String> listener) {
            if (listener == null) {
                Js.onCommand(line -> {});
            } else {
                Js.onCommand(line -> mainQueue.post(() -> listener.accept(line)));
            }
        }

        @Override
        public void print(String line) {
            Js.log(line);
        }
    }

    private static final class Info implements PlatformInfo {
        private final WebGl gl;

        Info(WebGl gl) {
            this.gl = gl;
        }

        @Override
        public String osName() {
            String platform = Js.platform();
            return platform.isEmpty() ? "Web" : "Web (" + platform + ")";
        }

        @Override
        public String architecture() {
            return "wasm";
        }

        @Override
        public boolean isWeb() {
            return true;
        }

        @Override
        public boolean isDevelopment() {
            return Js.isDevelopment();
        }

        @Override
        public String systemLocale() {
            return Js.language();
        }

        @Override
        public int screenWidth() {
            return Js.screenWidth();
        }

        @Override
        public int screenHeight() {
            return Js.screenHeight();
        }

        @Override
        public String gpuDescription() {
            String renderer = gl.getString(Gl.RENDERER);
            return renderer == null ? "WebGL2" : renderer;
        }
    }
}
