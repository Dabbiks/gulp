package dev.gulp.examples.platformer;

import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.entity.component.SpriteComponent;
import dev.gulp.api.module.GameModule;
import dev.gulp.api.module.ModuleInfo;
import dev.gulp.api.physics.Trigger;
import dev.gulp.api.physics.TriggerEnterEvent;
import dev.gulp.api.registry.Registries;

/** Coins: triggers on the pickup layer; touching one plays a sound and counts it on the HUD. */
@ModuleInfo(
        id = "coin",
        dependsOn = {"player", "hud"})
public final class CoinModule extends GameModule {

    static EntityType coin;

    @Override
    public void onLoad() {
        coin = registries()
                .register(
                        Registries.ENTITY_TYPE,
                        EntityType.builder(key("coin"))
                                .size(0.5f, 0.5f)
                                .component(() -> new SpriteComponent(GameAssets.Sprites.COIN))
                                .component(() -> new Trigger().layer(PlatformerGame.pickup))
                                .component(Bob::new)
                                .tags("coin")
                                .build());
    }

    @Override
    public void onEnable() {
        on(TriggerEnterEvent.class, e -> {
            Entity picked = e.trigger().entity();
            if (!picked.tags().has("coin") || !e.other().tags().has("player")) {
                return;
            }
            picked.get(Trigger.class).setEnabled(false);
            picked.world().playSound(picked.position(), PlatformerGame.pickupSound);
            picked.remove();
            require(HudModule.class).addCoin();
        });
    }

    /** Coins bob up and down. */
    static final class Bob extends Component {
        private float base = Float.NaN;
        private int ticks;

        @Override
        protected void onTick() {
            if (Float.isNaN(base)) {
                base = entity().y();
            }
            ticks++;
            entity().setPosition(entity().x(), base + (float) Math.sin(ticks * 0.08f) * 0.12f);
        }
    }
}
