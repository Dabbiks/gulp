package dev.gulp.examples.topdown;

import dev.gulp.api.Game;
import dev.gulp.api.GameSettings;
import dev.gulp.api.Gulp;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.render.StretchMode;

/**
 * An endless island world: chunks are generated from noise around the camera, water edges join up through a 16-tile
 * terrain, trees come with their chunk and can be chopped with a click, and the camera follows the player smoothly.
 *
 * <p>Controls: WASD, arrows or the left stick move; click a tree to chop it; Q and E zoom.
 */
public final class TopdownGame extends Game {

    /**
     * Starts the example on the best available backend.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        Gulp.launch(new TopdownGame());
    }

    @Override
    public String id() {
        return "topdown";
    }

    @Override
    public void configure(GameSettings settings) {
        settings.title("Gulp Topdown")
                .windowSize(960, 540)
                .baseResolution(480, 270)
                .stretchMode(StretchMode.VIEWPORT)
                .integerScaling(true)
                .pixelsPerUnit(16)
                .clearColor(Color.rgb(0x3a76c4))
                .modules(new OverworldModule());
    }

    @Override
    public void onStart() {
        // OverworldModule generates and opens the world when it is enabled.
    }
}
