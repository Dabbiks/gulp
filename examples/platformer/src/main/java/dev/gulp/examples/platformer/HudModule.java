package dev.gulp.examples.platformer;

import static dev.gulp.api.ui.Ui.*;

import dev.gulp.api.data.DataType;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.component.Health;
import dev.gulp.api.module.GameModule;
import dev.gulp.api.module.ModuleInfo;
import dev.gulp.api.save.GameLoadEvent;
import dev.gulp.api.save.GameSaveEvent;
import dev.gulp.api.ui.Align;
import dev.gulp.api.ui.Anchor;
import dev.gulp.api.ui.Computed;
import dev.gulp.api.ui.State;
import dev.gulp.api.world.World;

/** The HUD from section 21: coins and hearts as widgets bound to states, anchored to the top-left corner. */
@ModuleInfo(id = "hud")
public final class HudModule extends GameModule {

    private final State<Integer> coins = State.of(0);
    private final State<Integer> left = State.of(0);
    private final State<Float> health = State.of(1f);

    /**
     * Returns the collected coins; the coin module adds to it.
     *
     * @return the state
     */
    public State<Integer> coins() {
        return coins;
    }

    @Override
    public void onEnable() {
        ui().hud()
                .add(
                        this,
                        panel(row(
                                                image(GameAssets.Sprites.COIN).size(10, 10),
                                                label(Computed.of(
                                                        () -> coins.get() + " / " + (coins.get() + left.get()))),
                                                progressBar(health).size(40, 5))
                                        .gap(4)
                                        .align(Align.CENTER))
                                .anchor(Anchor.TOP_LEFT)
                                .offset(4, 4));
        every(10, this::refresh);
        on(GameSaveEvent.class, e -> e.data().set(key("coins"), DataType.INT, coins.get()));
        on(GameLoadEvent.class, e -> coins.set(e.data().getOrDefault(key("coins"), DataType.INT, 0)));
    }

    private void refresh() {
        World world = worlds().active();
        if (world == null) {
            return;
        }
        left.set(world.query().tag("coin").count());
        Entity player = world.query().tag("player").first();
        if (player != null) {
            Health hp = player.get(Health.class);
            health.set(hp.current() / hp.max());
        }
    }
}
