package dev.gulp.backend.headless;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.platform.CursorMode;
import dev.gulp.platform.DecodedAudio;
import dev.gulp.platform.DecodedImage;
import dev.gulp.platform.GlyphBitmap;
import dev.gulp.platform.HttpResult;
import dev.gulp.platform.InputListener;
import dev.gulp.platform.PlatformCallback;
import dev.gulp.platform.PlatformFontFace;
import dev.gulp.platform.WebSocketListener;
import dev.gulp.platform.WindowListener;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class HeadlessDevicesTest {

    private final HeadlessBackend backend = HeadlessRunnerTest.backend();
    private final List<String> log = new ArrayList<>();

    /** Delivers queued callbacks and input, as the loop does before each frame. */
    private void nextFrame() {
        backend.loop().run(new HeadlessRunnerTest.StopAfter(Integer.MAX_VALUE));
        backend.loop().step(1);
        backend.loop().stop();
    }

    private <T extends @Nullable Object> PlatformCallback<T> recording(String name) {
        return new PlatformCallback<>() {
            @Override
            public void success(T result) {
                log.add(name + " ok " + describe(result));
            }

            @Override
            public void failure(Throwable error) {
                log.add(name + " failed " + error.getClass().getSimpleName());
            }
        };
    }

    private static String describe(@Nullable Object result) {
        return switch (result) {
            case null -> "null";
            case ByteBuffer bytes -> new String(bytesOf(bytes), UTF_8);
            case DecodedImage image -> image.width() + "x" + image.height();
            case DecodedAudio audio -> audio.channels() + "ch@" + audio.sampleRate();
            case HttpResult http -> String.valueOf(http.status());
            default -> result.toString();
        };
    }

    private static byte[] bytesOf(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return bytes;
    }

    @Test
    void backendExposesAllServices() {
        assertThat(backend.name()).isEqualTo("headless");
        assertThat(backend.info().isWeb()).isFalse();
        assertThat(backend.info().isDevelopment()).isTrue();
        assertThat(backend.info().osName()).isEqualTo("headless");
        assertThat(backend.info().architecture()).isEqualTo("headless");
        assertThat(backend.info().screenWidth()).isEqualTo(1920);
        assertThat(backend.info().screenHeight()).isEqualTo(1080);
        assertThat(backend.info().gpuDescription()).contains("headless");
        assertThat(backend.info().systemLocale()).isEqualTo("en-US");
        backend.info().setSystemLocale("pl-PL");
        assertThat(backend.info().systemLocale()).isEqualTo("pl-PL");
    }

    @Test
    void windowSimulatesResizeFocusAndClose() {
        HeadlessWindow window = backend.window();
        window.setListener(new WindowListener() {
            @Override
            public void resized(int width, int height, int framebufferWidth, int framebufferHeight) {
                log.add("resized " + width + "x" + height + " fb " + framebufferWidth + "x" + framebufferHeight);
            }

            @Override
            public void focusChanged(boolean focused) {
                log.add("focus " + focused);
            }

            @Override
            public void contentScaleChanged(float scale) {
                log.add("scale " + scale);
            }

            @Override
            public void closeRequested() {
                log.add("close");
            }
        });

        assertThat(window.width()).isEqualTo(100);
        assertThat(window.height()).isEqualTo(50);
        window.simulateResize(200, 100, 1.5f);
        window.setSize(300, 150);
        window.simulateFocus(false);
        window.simulateCloseRequest();

        assertThat(log)
                .containsExactly(
                        "scale 1.5",
                        "resized 200x100 fb 300x150",
                        "resized 300x150 fb 450x225",
                        "focus false",
                        "close");
        assertThat(window.contentScale()).isEqualTo(1.5f);
        assertThat(window.isFocused()).isFalse();
        assertThat(window.shouldClose()).isTrue();
        assertThatThrownBy(() -> window.simulateResize(0, 1, 1f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> window.simulateResize(1, 1, 0f)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void windowStoresSettings() {
        HeadlessWindow window = backend.window();
        DecodedImage icon = new DecodedImage(1, 1, ByteBuffer.allocateDirect(4));

        window.setTitle("New title");
        window.setIcon(icon);
        window.setFullscreen(true);
        window.setVsync(false);
        window.setCursorMode(CursorMode.CAPTURED);
        window.setListener(null);
        window.simulateFocus(true);
        window.requestClose();

        assertThat(window.title()).isEqualTo("New title");
        assertThat(window.icon()).isSameAs(icon);
        assertThat(window.isFullscreen()).isTrue();
        assertThat(window.isVsync()).isFalse();
        assertThat(window.cursorMode()).isEqualTo(CursorMode.CAPTURED);
        assertThat(window.shouldClose()).isTrue();
    }

    @Test
    void inputDeliversInjectedEventsBeforeNextFrame() {
        HeadlessInput input = backend.input();
        input.setListener(new InputListener() {
            @Override
            public void keyDown(int keyCode, int scanCode, int modifiers, boolean repeat) {
                log.add("down " + keyCode);
            }

            @Override
            public void keyUp(int keyCode, int scanCode, int modifiers) {
                log.add("up " + keyCode);
            }

            @Override
            public void textTyped(int codePoint) {
                log.add("text " + Character.toString(codePoint));
            }

            @Override
            public void mouseMoved(float x, float y, float deltaX, float deltaY) {}

            @Override
            public void mouseButton(int button, boolean down, int modifiers) {}

            @Override
            public void scrolled(float deltaX, float deltaY) {}

            @Override
            public void touch(int pointer, int phase, float x, float y) {}

            @Override
            public void gamepadConnection(int index, boolean connected) {
                log.add("pad " + index + " " + connected);
            }
        });

        input.inject(l -> l.keyDown(44, 57, 0, false));
        input.inject(l -> l.textTyped('ą'));
        input.inject(l -> l.keyUp(44, 57, 0));
        assertThat(log).isEmpty();

        nextFrame();
        assertThat(log).containsExactly("down 44", "text ą", "up 44");

        input.setGamepad(1, "Test pad", new float[] {-0.5f}, new boolean[] {true, false});
        input.pollGamepads();
        input.pollGamepads();
        assertThat(input.isGamepadConnected(1)).isTrue();
        assertThat(input.gamepadName(1)).isEqualTo("Test pad");
        assertThat(input.gamepadAxis(1, 0)).isEqualTo(-0.5f);
        assertThat(input.gamepadAxis(1, 5)).isZero();
        assertThat(input.gamepadButton(1, 0)).isTrue();
        assertThat(input.gamepadButton(1, 1)).isFalse();
        assertThat(input.gamepadButton(1, 9)).isFalse();
        assertThat(input.rumble(1, 0.5f, 0.5f, 100)).isTrue();
        assertThat(input.rumbleCount()).isEqualTo(1);

        input.removeGamepad(1);
        input.pollGamepads();
        assertThat(log).endsWith("pad 1 true", "pad 1 false");
        assertThat(input.isGamepadConnected(1)).isFalse();
        assertThat(input.isGamepadConnected(-1)).isFalse();
        assertThat(input.isGamepadConnected(HeadlessInput.GAMEPAD_SLOTS)).isFalse();
        assertThat(input.gamepadName(1)).isNull();
        assertThat(input.gamepadAxis(1, 0)).isZero();
        assertThat(input.gamepadButton(1, 0)).isFalse();
        assertThat(input.rumble(1, 1f, 1f, 10)).isFalse();

        input.setListener(null);
        input.inject(l -> l.keyDown(1, 1, 0, false));
        input.setGamepad(0, "Silent", new float[0], new boolean[0]);
        input.pollGamepads();
        nextFrame();
        assertThat(log).endsWith("pad 1 false");
    }

    @Test
    void clipboardRoundTrips() {
        backend.input().writeClipboard("zażółć");
        backend.input().readClipboard(recording("clip"));

        assertThat(log).containsExactly("clip ok zażółć");
    }

    @Test
    void audioPoolsVoicesAndCounts() {
        HeadlessAudio audio = backend.audio();
        int buffer = audio.createBuffer(ShortBuffer.allocate(4), 2, 44_100);

        assertThat(audio.isUnlocked()).isTrue();
        int voice = audio.acquireVoice();
        audio.setGain(voice, 0.25f);
        audio.setPitch(voice, 1.1f);
        audio.setPan(voice, -1f);
        audio.play(voice, buffer, false);
        audio.setPaused(voice, true);
        assertThat(audio.isPlaying(voice)).isTrue();
        assertThat(audio.gain(voice)).isEqualTo(0.25f);
        audio.stop(voice);
        assertThat(audio.isPlaying(voice)).isFalse();
        audio.queue(voice, buffer);
        assertThat(audio.unqueueProcessed(voice)).isEqualTo(-1);
        audio.releaseVoice(voice);
        audio.deleteBuffer(buffer);
        audio.setMasterGain(0.5f);

        assertThat(audio.playCount()).isEqualTo(2);
        assertThat(audio.masterGain()).isEqualTo(0.5f);

        for (int i = 0; i < HeadlessAudio.VOICES; i++) {
            assertThat(audio.acquireVoice()).isNotNegative();
        }
        assertThat(audio.acquireVoice()).isEqualTo(-1);
    }

    @Test
    void filesKeepAssetsAndUserDataInMemory() {
        HeadlessFiles files = backend.files();
        files.putAsset("coins/config/game.yml", "speed: 7".getBytes(UTF_8));

        files.readAsset("coins/config/game.yml", recording("asset"));
        files.readAsset("missing.png", recording("missing"));
        files.writeUserData("saves/slot1.bin", ByteBuffer.wrap("abc".getBytes(UTF_8)), recording("write"));
        files.writeUserData("prefs.json", ByteBuffer.wrap("{}".getBytes(UTF_8)), recording("write"));
        files.readUserData("saves/slot1.bin", recording("user"));
        files.listUserData("saves/", recording("list"));
        assertThat(log).isEmpty();

        nextFrame();
        assertThat(log)
                .containsExactly(
                        "asset ok speed: 7",
                        "missing failed FileNotFoundException",
                        "write ok null",
                        "write ok null",
                        "user ok abc",
                        "list ok [saves/slot1.bin]");
        assertThat(files.userData("saves/slot1.bin")).isEqualTo("abc".getBytes(UTF_8));
        assertThat(files.userDataLocation()).contains("memory");

        files.deleteUserData("saves/slot1.bin", recording("delete"));
        assertThat(files.userData("saves/slot1.bin")).isNull();
    }

    @Test
    void decodersReturnStubsAndFailOnEmptyInput() {
        HeadlessDecoders decoders = backend.decoders();
        ByteBuffer someBytes = ByteBuffer.wrap(new byte[] {1, 2, 3});

        decoders.decodeImage(someBytes, recording("image"));
        decoders.decodeImage(ByteBuffer.allocate(0), recording("image"));
        decoders.decodeAudio(someBytes, recording("audio"));
        decoders.decodeAudio(ByteBuffer.allocate(0), recording("audio"));
        nextFrame();

        assertThat(log)
                .containsExactly(
                        "image ok 1x1",
                        "image failed IllegalArgumentException",
                        "audio ok 1ch@44100",
                        "audio failed IllegalArgumentException");

        java.util.concurrent.atomic.AtomicReference<PlatformFontFace> opened =
                new java.util.concurrent.atomic.AtomicReference<>();
        decoders.openFont(someBytes, new PlatformCallback<>() {
            @Override
            public void success(PlatformFontFace value) {
                opened.set(value);
            }

            @Override
            public void failure(Throwable error) {
                log.add("font failed " + error.getClass().getSimpleName());
            }
        });
        decoders.openFont(ByteBuffer.allocate(0), new PlatformCallback<>() {
            @Override
            public void success(PlatformFontFace value) {}

            @Override
            public void failure(Throwable error) {
                log.add("font failed " + error.getClass().getSimpleName());
            }
        });
        nextFrame();
        assertThat(log).endsWith("font failed IllegalArgumentException");
        PlatformFontFace face = opened.get();
        GlyphBitmap glyph = face.rasterize(face.glyphIndex('A'), 20f);
        assertThat(glyph.width()).isEqualTo(10);
        assertThat(glyph.coverage().remaining()).isEqualTo(100);
        assertThat(face.kerning(1, 2, 20f)).isZero();
        assertThat(face.ascent(10f) + face.descent(10f)).isEqualTo(10f);
        assertThat(face.lineHeight(10f)).isEqualTo(12f);
        face.dispose();
    }

    @Test
    void netAnswersRegisteredResponsesAndRecordsTraffic() {
        HeadlessNet net = backend.net();
        net.respond("https://example.com/ok", new HttpResult(200, Map.of(), ByteBuffer.allocate(0)));

        net.http("GET", "https://example.com/ok", Map.of(), null, recording("ok"));
        net.http("POST", "https://example.com/missing", Map.of(), ByteBuffer.allocate(1), recording("missing"));
        net.openUrl("https://gulp.dev");
        nextFrame();

        assertThat(log).containsExactly("ok ok 200", "missing ok 404");
        assertThat(net.requests()).containsExactly("GET https://example.com/ok", "POST https://example.com/missing");
        assertThat(net.openedUrls()).containsExactly("https://gulp.dev");
    }

    @Test
    void fakeWebSocketOpensRecordsAndCloses() {
        HeadlessNet.FakeWebSocket socket = backend.net().openWebSocket("wss://example.com", new WebSocketListener() {
            @Override
            public void opened() {
                log.add("opened");
            }

            @Override
            public void textMessage(String text) {
                log.add("text " + text);
            }

            @Override
            public void binaryMessage(ByteBuffer data) {}

            @Override
            public void closed(int code, String reason) {
                log.add("closed " + code);
            }

            @Override
            public void failed(Throwable error) {}
        });

        socket.send("ping");
        socket.send(ByteBuffer.allocate(2));
        socket.receive("pong");
        socket.close(1000, "bye");
        socket.close(1000, "again");
        nextFrame();

        assertThat(log).containsExactly("opened", "text pong", "closed 1000");
        assertThat(socket.sentText()).containsExactly("ping");
        assertThat(socket.sentBinaryCount()).isEqualTo(1);
        assertThatThrownBy(() -> socket.send("late")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void executorRunsSynchronouslyUntilShutdown() {
        HeadlessExecutor executor = backend.executor();

        executor.execute(() -> log.add("task"));

        assertThat(log).containsExactly("task");
        assertThat(executor.executedCount()).isEqualTo(1);
        assertThat(executor.isConcurrent()).isFalse();
        backend.dispose();
        assertThatThrownBy(() -> executor.execute(() -> {})).isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    void modulesLookUpRegisteredCode() {
        HeadlessModules modules = backend.modules();
        Runnable generated = () -> {};

        modules.register(Runnable.class, String.class, generated);

        assertThat(modules.lookup(Runnable.class, String.class)).isSameAs(generated);
        assertThat(modules.lookup(Runnable.class, Integer.class)).isNull();
        assertThat(modules.lookup(CharSequence.class, String.class)).isNull();
    }
}
