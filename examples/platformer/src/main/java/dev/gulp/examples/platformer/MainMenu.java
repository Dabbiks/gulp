package dev.gulp.examples.platformer;

import static dev.gulp.api.ui.Ui.*;

import dev.gulp.api.ui.Node;
import dev.gulp.api.ui.Screen;
import dev.gulp.api.ui.Transitions;

/** The main menu from section 18.1: buttons one below the other, centred at any resolution, without a coordinate. */
public final class MainMenu extends Screen {

    /** Creates the menu; it dims the level behind it. */
    public MainMenu() {
        dimBackground(true);
    }

    @Override
    protected Node<?> build() {
        return center(column(
                        label("Coin Hunter").variant("title"),
                        button("Play").variant("primary").onClick(() -> {
                            close();
                            if (worlds().active() == null) {
                                worlds().switchTo("level1", Transitions.circleWipe(0.5f));
                            }
                        }),
                        button("Quit").onClick(() -> engine().stop()))
                .gap(6)
                .width(120));
    }
}
