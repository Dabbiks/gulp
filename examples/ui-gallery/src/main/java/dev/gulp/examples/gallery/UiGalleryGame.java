package dev.gulp.examples.gallery;

import dev.gulp.api.Game;
import dev.gulp.api.GameSettings;
import dev.gulp.api.Gulp;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.input.GamepadButton;
import dev.gulp.api.input.InputAction;
import dev.gulp.api.input.Keys;
import dev.gulp.api.registry.Registries;

/**
 * The UI gallery: one screen with tabs that show every container and widget. Everything works with the mouse, touch,
 * the keyboard (arrows, Enter, Escape, Tab, Page Up and Page Down) and with a gamepad alone (D-pad or stick, A, B,
 * the shoulder buttons to switch tabs, X for context menus). The tilde key opens the console, F12 the inspector.
 */
public final class UiGalleryGame extends Game {

    /** An action to show rebinding. */
    static InputAction jump;

    /**
     * Starts the gallery.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        Gulp.launch(new UiGalleryGame());
    }

    @Override
    public String id() {
        return "gallery";
    }

    @Override
    public void configure(GameSettings settings) {
        settings.title("Gulp UI Gallery")
                .windowSize(1280, 760)
                .clearColor(Color.rgb(0x10131c))
                .developerConsole(true);
    }

    @Override
    public void onLoad() {
        jump = registries()
                .register(
                        Registries.INPUT_ACTION,
                        InputAction.builder(key("jump"))
                                .bind(Keys.SPACE, GamepadButton.SOUTH)
                                .build());
    }

    @Override
    public void onStart() {
        ui().open(new GalleryScreen());
        logger().info("UI gallery started");
    }
}
