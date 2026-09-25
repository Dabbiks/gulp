package dev.gulp.examples.showcase;

import dev.gulp.api.Game;
import dev.gulp.api.GameSettings;
import dev.gulp.api.Gulp;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.input.KeyPressEvent;
import dev.gulp.api.input.Keys;

/**
 * Showcase of every framework feature, one screen per feature (Tab switches them), with a main menu, a pause menu on
 * Escape and settings built from UI widgets.
 */
public final class ShowcaseGame extends Game {

    private static final int SCREENS = 3;
    private static int screen;

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
                .modules(
                        new CoreDemoModule(),
                        new SpritesDemoModule(),
                        new TextDemoModule(),
                        new InputAudioDemoModule(),
                        new JuiceDemoModule(),
                        new MenuDemoModule());
    }

    /**
     * Returns the screen shown: 0 text, 1 input and audio, 2 juice (animations, particles, light). Tab switches.
     *
     * @return the screen number
     */
    static int screen() {
        return screen;
    }

    @Override
    public void onStart() {
        on(KeyPressEvent.class, e -> {
            if (e.key().equals(Keys.TAB) && !e.isConsumedByUi()) {
                screen = (screen + 1) % SCREENS;
            }
        });
        screen = config().getInt("screen", 0);
        logger().info("Showcase started (difficulty: " + config().getString("difficulty", "normal")
                + "). Tab switches screens; type /help in the terminal.");
    }
}
