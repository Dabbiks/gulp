package dev.gulp.examples.platformer;

import dev.gulp.api.Game;
import dev.gulp.api.GameSettings;
import dev.gulp.api.Gulp;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.render.StretchMode;

/**
 * Two LDtk levels side by side: IntGrid ground with auto-layer tiles, a decoration layer, coins and the player placed
 * as entities, and a sky and hills in parallax. Movement uses a few lines of tile collision until physics arrives in
 * stage 7.
 *
 * <p>Controls: A/D or arrows walk, Space or W jumps; the gamepad works too.
 */
public final class PlatformerGame extends Game {

    /**
     * Starts the example on the best available backend.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        Gulp.launch(new PlatformerGame());
    }

    @Override
    public String id() {
        return "platformer";
    }

    @Override
    public void configure(GameSettings settings) {
        settings.title("Gulp Platformer")
                .windowSize(960, 540)
                .baseResolution(480, 270)
                .stretchMode(StretchMode.VIEWPORT)
                .integerScaling(true)
                .pixelsPerUnit(16)
                .clearColor(Color.rgb(0x6ea8ec))
                .modules(new LevelModule());
    }

    @Override
    public void onStart() {
        // LevelModule registers and opens the world when it is enabled.
    }
}
