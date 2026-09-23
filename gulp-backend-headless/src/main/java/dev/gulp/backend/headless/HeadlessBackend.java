package dev.gulp.backend.headless;

import dev.gulp.platform.PlatformBackend;
import dev.gulp.platform.WindowConfig;
import java.util.ArrayDeque;

/**
 * Platform without window or sound, for unit tests, integration tests, CI and simulations. Frames advance only when
 * {@link HeadlessLoop#step(int)} is called, with simulated time, so games run faster than real time and
 * deterministically.
 *
 * <p>Asynchronous callbacks (files, decoders, network) are delivered at the start of the next frame, just like on
 * real platforms; the executor runs tasks synchronously.
 *
 * <pre>{@code
 * HeadlessBackend backend = new HeadlessBackend(new WindowConfig("test", 320, 180, false, false, true));
 * backend.files().putAsset("coins/config/game.yml", "speed: 7".getBytes(UTF_8));
 * }</pre>
 */
public final class HeadlessBackend implements PlatformBackend {

    private final ArrayDeque<Runnable> mainQueue = new ArrayDeque<>();
    private final HeadlessWindow window;
    private final HeadlessGl gl = new HeadlessGl();
    private final HeadlessInput input = new HeadlessInput();
    private final HeadlessAudio audio = new HeadlessAudio();
    private final HeadlessFiles files = new HeadlessFiles(mainQueue::add);
    private final HeadlessDecoders decoders = new HeadlessDecoders(mainQueue::add);
    private final HeadlessExecutor executor = new HeadlessExecutor();
    private final HeadlessNet net = new HeadlessNet(mainQueue::add);
    private final HeadlessInfo info = new HeadlessInfo();
    private final HeadlessModules modules = new HeadlessModules();
    private final HeadlessLoop loop;

    /**
     * Creates a headless platform.
     *
     * @param config window parameters; the size becomes the fixed framebuffer size
     */
    public HeadlessBackend(WindowConfig config) {
        this.window = new HeadlessWindow(config);
        this.loop = new HeadlessLoop(this::beforeFrame);
    }

    private void beforeFrame() {
        for (int pending = mainQueue.size(); pending > 0; pending--) {
            mainQueue.removeFirst().run();
        }
        input.deliverQueued();
    }

    @Override
    public String name() {
        return HeadlessGameLauncher.NAME;
    }

    @Override
    public HeadlessLoop loop() {
        return loop;
    }

    @Override
    public HeadlessGl gl() {
        return gl;
    }

    @Override
    public HeadlessWindow window() {
        return window;
    }

    @Override
    public HeadlessInput input() {
        return input;
    }

    @Override
    public HeadlessAudio audio() {
        return audio;
    }

    @Override
    public HeadlessFiles files() {
        return files;
    }

    @Override
    public HeadlessDecoders decoders() {
        return decoders;
    }

    @Override
    public HeadlessExecutor executor() {
        return executor;
    }

    @Override
    public HeadlessNet net() {
        return net;
    }

    @Override
    public HeadlessInfo info() {
        return info;
    }

    @Override
    public HeadlessModules modules() {
        return modules;
    }

    @Override
    public void dispose() {
        executor.shutdown();
        mainQueue.clear();
    }
}
