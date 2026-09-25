package dev.gulp.examples.showcase;

import static dev.gulp.api.ui.Ui.*;

import dev.gulp.api.audio.Audio;
import dev.gulp.api.input.InputAction;
import dev.gulp.api.input.KeyPressEvent;
import dev.gulp.api.input.Keys;
import dev.gulp.api.module.GameModule;
import dev.gulp.api.module.ModuleInfo;
import dev.gulp.api.registry.Key;
import dev.gulp.api.registry.Registries;
import dev.gulp.api.render.GameWindow;
import dev.gulp.api.ui.Align;
import dev.gulp.api.ui.Grid;
import dev.gulp.api.ui.Node;
import dev.gulp.api.ui.Screen;
import dev.gulp.api.ui.ScreenTransition;
import dev.gulp.api.ui.State;
import dev.gulp.api.ui.Theme;
import java.util.List;

/**
 * Menus built from containers and widgets only: the main menu at start, a pause menu on Escape and settings (display,
 * audio, controls). Not a single coordinate: everything is placed by rows, columns, grids and centring.
 */
@ModuleInfo(id = "menu")
public final class MenuDemoModule extends GameModule {

    private static final List<String> THEMES = List.of("Dark", "Light", "Pixel");

    @Override
    public void onEnable() {
        ui().open(new MainMenu());
        on(KeyPressEvent.class, e -> {
            if (e.key().equals(Keys.ESCAPE) && !e.isConsumedByUi() && ui().current() == null) {
                ui().push(new PauseScreen());
            }
        });
    }

    /** The first screen. */
    private final class MainMenu extends Screen {
        MainMenu() {
            dimBackground(true);
        }

        @Override
        protected Node<?> build() {
            return center(panel(column(
                                    label("Gulp Showcase").variant("title"),
                                    label("Tab switches demo screens; Escape opens the menu.")
                                            .variant("caption")
                                            .wrap(true),
                                    button("Start").variant("primary").onClick(() -> {
                                        close();
                                        logger().info("Menu closed");
                                    }),
                                    button("Settings").onClick(() -> ui().push(new SettingsScreen())),
                                    button("Quit").variant("danger").onClick(() -> engine().stop()))
                            .gap(10))
                    .width(320));
        }

        @Override
        protected void onBack() {
            close();
            logger().info("Menu closed");
        }
    }

    /** Pauses the game over the running demo. */
    private final class PauseScreen extends Screen {
        PauseScreen() {
            pausesGame(true).dimBackground(true).enter(ScreenTransition.scale(0.15f));
        }

        @Override
        protected Node<?> build() {
            return center(panel(column(
                                    label("Paused").variant("title"),
                                    button("Resume").variant("primary").onClick(this::close),
                                    button("Settings").onClick(() -> ui().push(new SettingsScreen())),
                                    button("Main menu").onClick(() -> ui().open(new MainMenu())))
                            .gap(10))
                    .width(280));
        }
    }

    /** Display, audio and control settings, all bound to states. */
    private final class SettingsScreen extends Screen {
        SettingsScreen() {
            dimBackground(true).enter(ScreenTransition.slideUp(0.2f));
        }

        @Override
        protected Node<?> build() {
            return center(panel(column(
                                    label("Settings").variant("title"),
                                    tabs(
                                                    tab("Display", displayPage()),
                                                    tab("Audio", audioPage()),
                                                    tab("Controls", controls()))
                                            .minSize(0, 260),
                                    button("Back").onClick(this::close).alignX(Align.END))
                            .gap(12))
                    .width(520));
        }

        private Node<?> displayPage() {
            GameWindow window = display().window();
            State<String> theme = State.of(themeName(ui().theme()));
            theme.subscribe(name -> ui().setTheme(
                            switch (name) {
                                case "Light" -> Theme.LIGHT;
                                case "Pixel" -> Theme.PIXEL;
                                default -> Theme.DARK;
                            }));
            State<Float> scale = State.of(ui().scale());
            scale.subscribe(ui()::setScale);
            return grid(
                            2,
                            label("Fullscreen"),
                            toggle("").checked(window.isFullscreen()).onChange(window::setFullscreen),
                            label("VSync"),
                            toggle("").checked(window.isVsync()).onChange(window::setVsync),
                            label("Theme"),
                            dropdown(THEMES, t -> t).bind(theme).width(160),
                            label("UI scale"),
                            slider(0.75f, 1.5f).step(0.25f).bind(scale).width(200))
                    .hGap(24)
                    .vGap(10);
        }

        private Node<?> audioPage() {
            Grid grid = grid(2).hGap(24).vGap(10);
            for (String bus : List.of(Audio.MASTER, Audio.MUSIC, Audio.SFX, Audio.UI)) {
                State<Float> volume = State.of(audio().bus(bus).volume());
                volume.subscribe(v -> audio().bus(bus).setVolume(v));
                grid.add(label(bus), slider(0, 1).bind(volume).width(200));
            }
            return grid;
        }

        private Node<?> controls() {
            Grid grid = grid(3).hGap(12).vGap(8);
            for (String name : List.of("move_left", "move_right", "move_up", "move_down", "jump", "fire")) {
                InputAction action = registries().get(Registries.INPUT_ACTION).get(Key.of("showcase", name));
                if (action != null) {
                    grid.add(label(name.replace('_', ' ')), keybindButton(action, 0), keybindButton(action, 1));
                }
            }
            return scroll(grid);
        }
    }

    private static String themeName(Theme theme) {
        String path = theme.key().path();
        return path.startsWith("light") ? "Light" : path.startsWith("pixel") ? "Pixel" : "Dark";
    }
}
