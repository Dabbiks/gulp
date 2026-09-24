package dev.gulp.examples.platformer;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.component.Health;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.module.GameModule;
import dev.gulp.api.module.ModuleInfo;
import dev.gulp.api.render.RenderLayerEvent;
import dev.gulp.api.text.TextStyle;
import dev.gulp.api.world.World;

/** Counts coins and draws the coins and hearts; widgets and state bindings replace it in stage 9. */
@ModuleInfo(id = "hud")
public final class HudModule extends GameModule {

    private int coins;

    /** Counts one more coin. */
    void addCoin() {
        coins++;
    }

    /**
     * Returns the coins collected.
     *
     * @return the count
     */
    int coins() {
        return coins;
    }

    @Override
    public void onEnable() {
        on(RenderLayerEvent.class, e -> {
            World world = worlds().active();
            if (!e.layer().name().equals("overlay") || world == null) {
                return;
            }
            int left = world.query().tag("coin").count();
            float hearts = 0f;
            Entity player = world.query().tag("player").first();
            if (player != null) {
                hearts = player.get(Health.class).current();
            }
            e.draw()
                    .color(Color.rgba(0x00000088))
                    .rect(4, 4, 150, 18)
                    .color(Color.WHITE)
                    .text("Monety: " + coins + " / " + (coins + left), 8, 7, TextStyle.of(10));
            for (int i = 0; i < 3; i++) {
                e.draw()
                        .color(i < hearts ? Color.rgb(0xff004d) : Color.rgba(0xffffff44))
                        .circle(110 + i * 12, 13, 4);
            }
            e.draw().color(Color.WHITE);
        });
    }
}
