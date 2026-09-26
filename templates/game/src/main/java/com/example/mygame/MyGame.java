package com.example.mygame;

import static dev.gulp.api.ui.Ui.*;

import dev.gulp.api.Game;
import dev.gulp.api.GameSettings;
import dev.gulp.api.Gulp;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.entity.component.SpriteComponent;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.input.GamepadAxis;
import dev.gulp.api.input.InputAction;
import dev.gulp.api.input.Keys;
import dev.gulp.api.registry.Registries;
import dev.gulp.api.render.StretchMode;
import dev.gulp.api.text.Text;
import dev.gulp.api.ui.Anchor;
import dev.gulp.api.world.World;
import dev.gulp.api.world.WorldSettings;

/**
 * A new Gulp game: a square that walks with WASD, the arrows or a gamepad, a translated HUD, and a module that counts
 * the steps. Start here: {@code ./gradlew runDesktop}, {@code ./gradlew runWeb --continuous}, F3 for the developer
 * overlay, {@code ~} for the console.
 */
public final class MyGame extends Game {

    static InputAction moveX;
    static InputAction moveLeft;
    static InputAction moveY;
    static InputAction moveUp;
    static EntityType player;

    /**
     * Starts the game on the best available backend.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        Gulp.launch(new MyGame());
    }

    @Override
    public String id() {
        return "mygame";
    }

    @Override
    public void configure(GameSettings settings) {
        settings.title("My Game")
                .windowSize(1280, 720)
                .baseResolution(640, 360)
                .stretchMode(StretchMode.VIEWPORT)
                .pixelsPerUnit(16)
                .clearColor(Color.rgb(0x1d2b53))
                .modules(new StepsModule());
    }

    @Override
    public void onLoad() {
        var r = registries();
        moveLeft = r.register(Registries.INPUT_ACTION,
                InputAction.builder(key("left")).bind(Keys.A, Keys.LEFT, GamepadAxis.LEFT_X.negative()).build());
        moveX = r.register(Registries.INPUT_ACTION,
                InputAction.builder(key("right")).bind(Keys.D, Keys.RIGHT, GamepadAxis.LEFT_X.positive()).build());
        moveUp = r.register(Registries.INPUT_ACTION,
                InputAction.builder(key("up")).bind(Keys.W, Keys.UP, GamepadAxis.LEFT_Y.negative()).build());
        moveY = r.register(Registries.INPUT_ACTION,
                InputAction.builder(key("down")).bind(Keys.S, Keys.DOWN, GamepadAxis.LEFT_Y.positive()).build());
    }

    @Override
    public void onStart() {
        Pixmap pixmap = new Pixmap(16, 16);
        pixmap.fill(Color.rgb(0xffa300));
        TextureRegion square = graphics().texture(pixmap).region();
        player = EntityType.builder(key("player"))
                .size(1f, 1f)
                .component(() -> new SpriteComponent(square))
                .component(Walker::new)
                .tags("player")
                .build();
        World world = worlds().create("main", WorldSettings.DEFAULT);
        world.spawn(player, 0, 0);
        world.camera().setZoom(2f);
        worlds().switchTo("main");
        ui().hud().add(this, panel(label(Text.translatable("hud.hello"))).anchor(Anchor.TOP_LEFT).offset(8, 8));
    }
}
