package dev.gulp.examples.platformer;

import dev.gulp.api.anim.Animator;
import dev.gulp.api.anim.Props;
import dev.gulp.api.anim.Tweens;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.entity.component.SpriteComponent;
import dev.gulp.api.math.Ease;
import dev.gulp.api.module.GameModule;
import dev.gulp.api.module.ModuleInfo;
import dev.gulp.api.physics.Trigger;
import dev.gulp.api.physics.TriggerEnterEvent;
import dev.gulp.api.registry.Registries;
import dev.gulp.api.ui.State;

/** Coins: spinning triggers on the pickup layer; touching one plays a sound, floats it away and counts it. */
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
                                .component(() -> new Animator(GameAssets.Animations.COIN).play("spin"))
                                .component(() -> new Trigger().layer(PlatformerGame.pickup))
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
            picked.tags().remove("coin");
            picked.world().playSound(picked.position(), PlatformerGame.pickupSound);
            Tweens.parallel(
                            Tweens.by(picked, Props.Y, -1f, 0.25f).ease(Ease.OUT_QUAD),
                            Tweens.to(picked, Props.ALPHA, 0f, 0.25f))
                    .onComplete(picked::remove)
                    .start();
            State<Integer> coins = require(HudModule.class).coins();
            coins.set(coins.get() + 1);
            if (saves().autosaveSlot() != null && !saves().isBusy()) {
                saves().slot(PlatformerGame.SLOT).save();
            }
        });
    }
}
