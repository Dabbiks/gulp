package dev.gulp.examples.platformer;

import dev.gulp.api.Game;
import dev.gulp.api.GameSettings;
import dev.gulp.api.Gulp;
import dev.gulp.api.audio.Sound;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.input.GamepadAxis;
import dev.gulp.api.input.GamepadButton;
import dev.gulp.api.input.InputAction;
import dev.gulp.api.input.Keys;
import dev.gulp.api.physics.CollisionLayer;
import dev.gulp.api.registry.Registries;
import dev.gulp.api.render.StretchMode;
import dev.gulp.api.ui.Transitions;
import dev.gulp.api.world.WorldSettings;
import dev.gulp.api.world.WorldSource;

/**
 * The coin platformer of the specification's section 21, as far as stage 7 goes: two LDtk levels, a player on a
 * {@link dev.gulp.api.physics.Mover} with coyote time and a jump buffer, coins as triggers on their own collision
 * layer, slimes to stomp, a lift riding a path from the map, and parallax hills. The animations, tweens and UI of
 * section 21 arrive with stages 8 and 9.
 *
 * <p>Controls: A/D or arrows walk, Space jumps, S + Space drops through platforms; the gamepad works too.
 */
public final class PlatformerGame extends Game {

    static InputAction moveLeft;
    static InputAction moveRight;
    static InputAction jump;
    static InputAction down;
    static CollisionLayer pickup;
    static Sound pickupSound;

    /**
     * Starts the example on the best available backend.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        Gulp.launch(new PlatformerGame());
    }

    @Override
    public String id() {
        return "platformer";
    }

    @Override
    public void configure(GameSettings settings) {
        settings.title("Coin Hunter")
                .windowSize(960, 540)
                .baseResolution(480, 270)
                .stretchMode(StretchMode.VIEWPORT)
                .integerScaling(true)
                .pixelsPerUnit(16)
                .clearColor(Color.rgb(0x6ea8ec))
                .modules(new HudModule(), new PlayerModule(), new CoinModule(), new EnemyModule());
    }

    @Override
    public void onLoad() {
        var r = registries();
        moveLeft = r.register(
                Registries.INPUT_ACTION,
                InputAction.builder(key("move_left"))
                        .bind(Keys.A, Keys.LEFT, GamepadAxis.LEFT_X.negative(), GamepadButton.DPAD_LEFT)
                        .build());
        moveRight = r.register(
                Registries.INPUT_ACTION,
                InputAction.builder(key("move_right"))
                        .bind(Keys.D, Keys.RIGHT, GamepadAxis.LEFT_X.positive(), GamepadButton.DPAD_RIGHT)
                        .build());
        jump = r.register(
                Registries.INPUT_ACTION,
                InputAction.builder(key("jump"))
                        .bind(Keys.SPACE, Keys.W, Keys.UP, GamepadButton.SOUTH)
                        .build());
        down = r.register(
                Registries.INPUT_ACTION,
                InputAction.builder(key("down"))
                        .bind(Keys.S, Keys.DOWN, GamepadAxis.LEFT_Y.positive(), GamepadButton.DPAD_DOWN)
                        .build());
        pickup = r.register(Registries.COLLISION_LAYER, CollisionLayer.of(key("pickup")));
        pickupSound = r.register(
                Registries.SOUND,
                Sound.builder(key("pickup"))
                        .file(GameAssets.Sounds.PICKUP)
                        .pitchRange(0.9f, 1.1f)
                        .maxInstances(4)
                        .build());
    }

    @Override
    public void onStart() {
        worlds().register(
                        "level1",
                        WorldSource.ldtk(GameAssets.Maps.WORLD)
                                .settings(WorldSettings.DEFAULT.tileSize(16))
                                .spawn("Player", PlayerModule.player)
                                .spawn("Coin", CoinModule.coin)
                                .spawn("Slime", EnemyModule.slime)
                                .spawner(EnemyModule::spawnLift)
                                .tile("platform", PlayerModule.platform)
                                .onLoad(world -> {
                                    world.parallax()
                                            .layer(GameAssets.Textures.SKY, 0f)
                                            .repeatX()
                                            .offset(0, -8.5f);
                                    world.parallax()
                                            .layer(GameAssets.Textures.HILLS, 0.3f)
                                            .repeatX()
                                            .offset(0, -2f);
                                }));
        worlds().switchTo("level1", Transitions.circleWipe(0.5f));
    }
}
