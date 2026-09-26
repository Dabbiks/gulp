package com.example.mygame;

import dev.gulp.api.module.GameModule;
import dev.gulp.api.module.ModuleInfo;

/** A module: logs the player's steps every five seconds. Modules can be switched off with {@code /module disable}. */
@ModuleInfo(id = "steps")
public final class StepsModule extends GameModule {

    /** Creates the module. */
    public StepsModule() {}

    @Override
    public void onEnable() {
        every(300, () -> {
            var world = worlds().active();
            var player = world == null ? null : world.query().tag("player").first();
            if (player != null) {
                logger().info("Steps so far: " + player.get(Walker.class).steps());
            }
        });
    }
}
