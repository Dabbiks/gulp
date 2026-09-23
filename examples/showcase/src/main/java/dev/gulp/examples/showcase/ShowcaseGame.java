package dev.gulp.examples.showcase;

import dev.gulp.api.Game;
import dev.gulp.api.GameSettings;
import dev.gulp.api.Gulp;
import dev.gulp.api.graphics.Color;

/**
 * Showcase of every framework feature, one screen per feature. Stage 0: an empty window cleared with a background
 * color.
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
        settings.title("Gulp Showcase").windowSize(960, 540).clearColor(Color.rgb(0x1d2b53));
    }

    @Override
    public void onStart() {}
}
