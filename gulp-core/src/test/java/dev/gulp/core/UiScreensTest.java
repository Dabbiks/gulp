package dev.gulp.core;

import static dev.gulp.api.ui.Ui.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.anim.Props;
import dev.gulp.api.anim.Tween;
import dev.gulp.api.anim.Tweens;
import dev.gulp.api.event.Subscription;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.input.ActionSet;
import dev.gulp.api.input.Keys;
import dev.gulp.api.registry.Key;
import dev.gulp.api.registry.Registries;
import dev.gulp.api.scheduler.Task;
import dev.gulp.api.text.TextAlign;
import dev.gulp.api.text.TextStyle;
import dev.gulp.api.ui.Anchor;
import dev.gulp.api.ui.Button;
import dev.gulp.api.ui.Dialog;
import dev.gulp.api.ui.Node;
import dev.gulp.api.ui.ScreenCloseEvent;
import dev.gulp.api.ui.ScreenOpenEvent;
import dev.gulp.api.ui.ScreenTransition;
import dev.gulp.api.ui.Style;
import dev.gulp.api.ui.StyleBox;
import dev.gulp.api.ui.Theme;
import dev.gulp.api.ui.UiAction;
import dev.gulp.api.ui.WidgetState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UiScreensTest extends UiFixture {

    @Test
    void screensStackPauseBlockInputAndCleanUp() {
        startUi();
        List<String> events = new ArrayList<>();
        game.on(ScreenOpenEvent.class, e -> events.add("open"));
        game.on(ScreenCloseEvent.class, e -> events.add("close"));
        Button play = button("Play");
        TestScreen menu = open(center(column(play, button("Quit")).gap(8f)));
        assertThat(menu.isOpen()).isTrue();
        assertThat(menu.opened).isEqualTo(1);
        assertThat(ui.current()).isSameAs(menu);
        assertThat(ui.focused()).isSameAs(play);
        assertThat(game.engine().input().isEnabled(ActionSet.GAMEPLAY)).isFalse();
        assertThat(game.engine().isPaused()).isFalse();
        Task task = menu.every(1, () -> {});

        TestScreen pause = new TestScreen(() -> center(label("Paused")));
        pause.pausesGame(true)
                .dimBackground(true)
                .enter(ScreenTransition.slideUp(0.1f))
                .exit(ScreenTransition.scale(0.1f));
        ui.push(pause);
        step(3);
        assertThat(game.engine().isPaused()).isTrue();
        assertThat(ui.screens()).containsExactly(menu, pause);
        assertThatThrownBy(() -> ui.push(pause)).isInstanceOf(IllegalStateException.class);
        key(Keys.ESCAPE);
        assertThat(pause.backs).isEqualTo(1);
        assertThat(pause.isOpen()).isFalse();
        assertThat(game.engine().isPaused()).isFalse();
        assertThat(ui.focused()).isSameAs(play);
        step(10);

        TestScreen other = new TestScreen(() -> label("Other"));
        ui.open(other);
        step(1);
        assertThat(menu.closed).isEqualTo(1);
        assertThat(task.isCancelled()).isTrue();
        assertThat(ui.screens()).containsExactly(other);
        other.close();
        assertThat(ui.pop()).isNull();
        assertThat(game.engine().input().isEnabled(ActionSet.GAMEPLAY)).isTrue();
        assertThat(events).containsExactly("open", "open", "close", "open", "close", "close");

        game.on(ScreenOpenEvent.class, e -> e.setCancelled(true));
        ui.push(new TestScreen(() -> label("never")));
        assertThat(ui.current()).isNull();
        assertThat(menu.id()).startsWith("screen:");
        assertThat(menu.logger()).isNotNull();
        assertThat(menu.config()).isNotNull();
    }

    @Test
    void dialogsModalsOverlaysAndToasts() {
        startUi();
        List<String> chosen = new ArrayList<>();
        Dialog dialog = dialog("Quit?", label("Progress is saved."))
                .button("Cancel", null)
                .button("Quit", "danger", () -> chosen.add("quit"))
                .width(300f);
        ui.push(dialog);
        step(20);
        assertThat(dialog.root()).isNotNull();
        Button quit = dialog.root().findAll(Button.class).get(1);
        assertThat(ui.focused()).isSameAs(quit);
        key(Keys.ENTER);
        assertThat(chosen).containsExactly("quit");
        assertThat(dialog.isOpen()).isFalse();
        step(20);

        ui.push(modal(label("modal").anchor(Anchor.TOP_LEFT)));
        step(2);
        key(Keys.ESCAPE);
        assertThat(ui.current()).isNull();

        List<Float> areas = new ArrayList<>();
        Subscription drawer = ui.overlay().draw(game, (draw, screen) -> {
            areas.add(screen.width());
            draw.text("Paused", screen.center(), TextStyle.of(20), TextAlign.CENTER);
            draw.rect(screen.anchor(Anchor.BOTTOM_RIGHT, 8f, 8f, 100f, 20f), Color.BLACK.withAlpha(0.5f));
            assertThat(screen.anchor(Anchor.BOTTOM_WIDE, 4f, 4f, 0f, 20f).width())
                    .isEqualTo(screen.width() - 8f);
        });
        ui.overlay().draw(game, (draw, screen) -> {
            throw new IllegalStateException("broken drawer");
        });
        step(2);
        assertThat(areas).isNotEmpty().allMatch(w -> w == 1280f);
        drawer.cancel();
        assertThat(drawer.isActive()).isFalse();
        int calls = areas.size();
        step(2);
        assertThat(areas).hasSize(calls);

        ui.toast("Saved");
        ui.toast("Failed", "danger", 1f);
        for (int i = 0; i < 6; i++) {
            ui.toast("spam " + i);
        }
        step(30);
        seconds(4f);
    }

    @Test
    void themesStylesScaleAndNodeTweens() {
        startUi();
        assertThat(ui.theme()).isSameAs(Theme.DEFAULT);
        assertThat(game.registries().get(Registries.THEME).keys())
                .contains(Key.of("gulp", "dark"), Key.of("gulp", "light"), Key.of("gulp", "pixel"));
        assertThat(game.registries()
                        .get(Registries.INPUT_ACTION)
                        .contains(UiAction.ACCEPT.action().key()))
                .isTrue();
        Theme purple = Theme.DARK.withAccent(Color.hex("#7c5cff"));
        assertThat(purple.resolve("slider", "", WidgetState.NORMAL).accent()).isEqualTo(Color.hex("#7c5cff"));
        assertThat(Theme.LIGHT.highContrast()).isNotSameAs(Theme.LIGHT);
        assertThat(Theme.LIGHT.highContrast().highContrast()).isSameAs(Theme.LIGHT.highContrast());
        Theme custom = Theme.builder(Key.of("test", "neon"))
                .parent(Theme.DARK)
                .style("button", s -> s.background(StyleBox.flat(Color.BLACK).border(2f, Color.RED)))
                .style("button", WidgetState.HOVER, s -> s.textColor(Color.RED))
                .variant("label", "neon", s -> s.textColor(Color.CYAN))
                .variant("label", "neon", WidgetState.HOVER, s -> s.textColor(Color.MAGENTA))
                .tooltipDelay(0.2f)
                .build();
        assertThat(custom.tooltipDelay()).isEqualTo(0.2f);
        assertThat(custom.resolve("label", "neon", WidgetState.HOVER).textColor())
                .isEqualTo(Color.MAGENTA);
        assertThat(custom.resolve("button", "", WidgetState.HOVER).textColor()).isEqualTo(Color.RED);
        assertThat(custom.withAccent(Color.GREEN)
                        .resolve("slider", "", WidgetState.NORMAL)
                        .accent())
                .isEqualTo(Color.GREEN);
        assertThat(custom.highContrast()).isSameAs(custom);
        assertThat(custom.toString()).contains("test:neon");

        Style style = new Style()
                .background(Color.RED)
                .background(Color.BLUE)
                .mutedColor(Color.GRAY)
                .padding(4f)
                .gap(2f)
                .track(Color.BLACK)
                .focusColor(Color.WHITE)
                .focusWidth(3f)
                .minSize(10f, 12f)
                .transition(0f)
                .bold(true);
        assertThat(style.copy().background()).isEqualTo(StyleBox.flat(Color.BLUE));
        assertThat(style.minWidth()).isEqualTo(10f);
        assertThat(style.textStyle().isBold()).isTrue();
        assertThat(new Style().mutedColor().a()).isEqualTo(0.5f);

        Button button = button("Styled").variant("primary").theme(custom).style(s -> s.fontSize(20f));
        ui.hud().add(game, button.anchor(Anchor.CENTER));
        ui.setTheme(Theme.LIGHT);
        ui.setTheme(Theme.LIGHT);
        step(2);
        assertThat(button.variant()).isEqualTo("primary");
        ui.setScale(1.5f);
        step(1);
        assertThat(ui.scale()).isEqualTo(1.5f);
        assertThat(game.engine().preferences().getFloat("gulp.ui.scale", 0f)).isEqualTo(1.5f);
        ui.setScale(9f);
        assertThat(ui.scale()).isEqualTo(2f);
        ui.setScale(1f);
        ui.pixelPerfect(true);
        assertThat(ui.isPixelPerfect()).isTrue();
        step(1);
        ui.pixelPerfect(false);

        Tween fade = Tweens.to(button, Props.NODE_ALPHA, 0f, 0.5f).start();
        Tweens.to(button, Props.NODE_SCALE, 1.2f, 0.5f).start();
        Tweens.to(button, Props.NODE_ROTATION, 10f, 0.5f).start();
        Tweens.to(button, Props.NODE_COLOR, Color.RED, 0.5f).start();
        Tweens.to(button, Props.NODE_OFFSET, new dev.gulp.api.math.Vec2(20f, 10f), 0.5f)
                .start();
        game.engine().pause();
        seconds(0.25f);
        assertThat(button.alpha()).isBetween(0.05f, 0.95f);
        seconds(0.4f);
        assertThat(button.alpha()).isZero();
        assertThat(button.offsetX()).isEqualTo(20f);
        game.engine().resume();
        Tween resize = Tweens.to(button, Props.NODE_SIZE, new dev.gulp.api.math.Vec2(200f, 50f), 5f)
                .start();
        step(2);
        ui.hud().remove(button);
        step(2);
        assertThat(resize.isRunning()).isFalse();
        assertThat(fade.isRunning()).isFalse();
        Node<?> plain = label("x");
        assertThat(plain.toString()).contains("Label");
    }
}
