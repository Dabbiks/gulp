package dev.gulp.examples.showcase;

import dev.gulp.api.Game;
import dev.gulp.api.GameSettings;
import dev.gulp.api.Gulp;
import dev.gulp.api.graphics.Color;

/**
 * Showcase of every framework feature, one screen per feature. Until screens exist (stage 9) each feature is a module
 * that reports to the log and the terminal console.
 */
public final class ShowcaseGame extends Game {

    /**
     * Starts the showcase on the best available backend.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        Gulp.launch(new ShowcaseGame());
    }

    @Override
    public String id() {
        return "showcase";
    }

    @Override
    public void configure(GameSettings settings) {
        settings.title("Gulp Showcase")
                .windowSize(960, 540)
                .clearColor(Color.rgb(0x1d2b53))
                .modules(new CoreDemoModule());
    }

    @Override
    public void onStart() {
        logger().info("Showcase started (difficulty: " + config().getString("difficulty", "normal")
                + "). Type /help in the terminal.");
    }
}
