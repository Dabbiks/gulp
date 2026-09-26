package dev.gulp.examples.platformer;

import static dev.gulp.api.ui.Ui.*;

import dev.gulp.api.ui.Node;
import dev.gulp.api.ui.Screen;
import dev.gulp.api.ui.State;
import dev.gulp.api.ui.Transitions;

/** The main menu from section 18.1: buttons one below the other, centred at any resolution, without a coordinate. */
public final class MainMenu extends Screen {

    /** Creates the menu; it dims the level behind it. */
    public MainMenu() {
        dimBackground(true);
    }

    @Override
    protected Node<?> build() {
        State<Boolean> saved = State.of(false);
        saves().slot(PlatformerGame.SLOT).exists().thenSync(saved::set);
        return center(column(
                        label("Coin Hunter").variant("title"),
                        button("Continue").variant("primary").enabled(saved).onClick(() -> {
                            close();
                            autosave();
                            saves().slot(PlatformerGame.SLOT).load().onFailure(error -> worlds().switchTo("level1"));
                        }),
                        button(saved.get() ? "New game" : "Play").onClick(() -> {
                            close();
                            autosave();
                            if (worlds().active() == null) {
                                worlds().switchTo("level1", Transitions.circleWipe(0.5f));
                            }
                        }),
                        button("Quit").onClick(() -> engine().stop()))
                .gap(6)
                .width(120));
    }

    /** Progress survives a closed window or tab: saved every 20 seconds, after each coin and when a tab is hidden. */
    private void autosave() {
        saves().autosave(PlatformerGame.SLOT, java.time.Duration.ofSeconds(20));
    }
}
