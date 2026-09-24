package dev.gulp.examples.sandbox;

import dev.gulp.api.Game;
import dev.gulp.api.GameSettings;
import dev.gulp.api.Gulp;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.render.StretchMode;

/**
 * Rigid body physics without a single asset: a stack of ten crates, a pyramid, a chain bridge between two posts, a
 * pendulum, bouncy balls and a car on sprung wheels. Everything is drawn from its body's shape.
 *
 * <p>Controls: drag bodies with the mouse; A/D or arrows drive the car; Space drops balls; R resets; Tab shows the
 * debug shapes, contacts and joints.
 */
public final class SandboxGame extends Game {

    /**
     * Starts the sandbox on the best available backend.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        Gulp.launch(new SandboxGame());
    }

    @Override
    public String id() {
        return "sandbox";
    }

    @Override
    public void configure(GameSettings settings) {
        settings.title("Gulp Sandbox")
                .windowSize(1280, 720)
                .baseResolution(960, 540)
                .stretchMode(StretchMode.VIEWPORT)
                .pixelsPerUnit(24)
                .clearColor(Color.rgb(0x1d2b53))
                .modules(new SandboxModule());
    }

    @Override
    public void onStart() {
        // SandboxModule builds the scene when it is enabled.
    }
}
