package dev.gulp.core;

import static dev.gulp.api.ui.Ui.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.entity.component.WorldUi;
import dev.gulp.api.input.ActionSet;
import dev.gulp.api.input.InputAction;
import dev.gulp.api.input.Keys;
import dev.gulp.api.registry.Key;
import dev.gulp.api.registry.Registries;
import dev.gulp.api.render.StretchMode;
import dev.gulp.api.ui.Anchor;
import dev.gulp.api.ui.Button;
import dev.gulp.api.ui.ProgressBar;
import dev.gulp.api.world.World;
import dev.gulp.api.world.WorldSettings;
import java.util.List;
import org.junit.jupiter.api.Test;

class UiToolsTest extends UiFixture {

    @Test
    void theConsoleRunsCommandsWithHistoryAndCompletion() {
        startUi(s -> s.developerConsole(true), b -> {});
        assertThat(ui.isConsoleOpen()).isFalse();
        key(Keys.GRAVE);
        assertThat(ui.isConsoleOpen()).isTrue();
        type("tps");
        key(Keys.ENTER);
        assertThat(game.engine().commands().console().output()).anyMatch(line -> line.startsWith("TPS"));
        type("/timesc");
        key(Keys.TAB);
        type("0.5");
        key(Keys.ENTER);
        assertThat(game.engine().timeScale()).isEqualTo(0.5f);
        key(Keys.UP);
        key(Keys.UP);
        key(Keys.DOWN);
        key(Keys.DOWN);
        type("/");
        key(Keys.TAB);
        key(Keys.BACKSPACE);
        step(2);
        key(Keys.ESCAPE);
        assertThat(ui.isConsoleOpen()).isFalse();
        ui.console(true);
        key(Keys.GRAVE);
        assertThat(ui.isConsoleOpen()).isFalse();
    }

    @Test
    void theInspectorDescribesTheNodeUnderThePointer() {
        startUi();
        Button target = button("Inspect me").id("target").variant("primary");
        open(stack(target.anchor(Anchor.CENTER).offset(4f, 4f)));
        ui.inspector(true);
        assertThat(ui.isInspectorOpen()).isTrue();
        move(target.x() + 3f, target.y() + 3f);
        step(2);
        assertThat(runner.engine().ui().inspected()).isSameAs(target);
        assertThat(target.layoutInfo()).contains("align");
        ui.inspector(false);
        step(1);
    }

    @Test
    void worldUiFollowsEntitiesThroughTheCamera() {
        startUi();
        EntityType slime =
                EntityType.builder(Key.of("test", "slime")).size(1f, 1f).build();
        World world = game.worlds().create("main", WorldSettings.DEFAULT);
        game.worlds().switchTo("main");
        step(1);
        ProgressBar bar = progressBar(0.5f).size(24f, 4f);
        Entity entity = world.spawn(slime, 0f, 0f);
        WorldUi component = entity.add(new WorldUi(bar).offset(0f, -1f).scaleWithZoom(true));
        step(2);
        assertThat(component.node()).isSameAs(bar);
        assertThat(component.scalesWithZoom()).isTrue();
        assertThat(component.offset().y()).isEqualTo(-1f);
        assertThat(bar.isMounted()).isTrue();
        assertThat(bar.width()).isEqualTo(24f);
        float before = bar.x();
        entity.teleport(2f, 0f);
        step(2);
        assertThat(bar.x()).isGreaterThan(before);
        entity.teleport(5000f, 0f);
        step(2);
        assertThat(bar.isVisible()).isFalse();
        entity.remove();
        step(1);
        assertThat(bar.isMounted()).isFalse();
    }

    @Test
    void uiDrawsSharpOnViewportDisplaysAndBlocksGameActions() {
        InputAction pause =
                InputAction.builder(Key.of("test", "pause")).bind(Keys.ESCAPE).build();
        startUi(s -> s.baseResolution(320, 180).stretchMode(StretchMode.VIEWPORT), b -> {});
        game.onLoad = () -> {};
        assertThat(pause.set()).isEqualTo(ActionSet.GAMEPLAY);
        List<Button> buttons = List.of(button("One"), button("Two"));
        open(center(column(buttons.get(0), buttons.get(1))));
        int calls = game.display().stats().drawCalls();
        assertThat(calls).isPositive();
        ui.pixelPerfect(true);
        step(2);
        ui.pixelPerfect(false);
        step(2);
        key(Keys.ESCAPE);
        assertThat(ui.current()).isNull();
        assertThat(game.engine().input().pressed(pause)).isFalse();
        assertThat(game.registries().get(Registries.INPUT_ACTION)).isNotNull();
    }
}
