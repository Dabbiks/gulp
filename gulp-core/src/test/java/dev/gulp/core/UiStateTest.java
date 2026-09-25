package dev.gulp.core;

import static dev.gulp.api.ui.Ui.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.gulp.api.event.Subscription;
import dev.gulp.api.ui.Anchor;
import dev.gulp.api.ui.Computed;
import dev.gulp.api.ui.Label;
import dev.gulp.api.ui.ListState;
import dev.gulp.api.ui.Observable;
import dev.gulp.api.ui.State;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UiStateTest extends UiFixture {

    @Test
    void statesNotifyOnChangeAndComputedValuesTrackTheirInputs() {
        State<Integer> hp = State.of(10);
        State<Integer> max = State.of(10);
        List<Integer> seen = new ArrayList<>();
        Subscription sub = hp.subscribe(seen::add);
        hp.set(10);
        hp.set(7);
        hp.update(v -> v - 2);
        assertThat(seen).containsExactly(7, 5);
        sub.cancel();
        sub.cancel();
        assertThat(sub.isActive()).isFalse();
        hp.set(1);
        assertThat(seen).hasSize(2);
        assertThat(hp.toString()).isEqualTo("State[1]");

        Computed<String> text = Computed.of(() -> hp.get() + "/" + max.get());
        assertThat(text.get()).isEqualTo("1/10");
        List<String> texts = new ArrayList<>();
        Subscription watching = text.subscribe(texts::add);
        max.set(20);
        hp.set(1);
        hp.set(4);
        assertThat(texts).containsExactly("1/20", "4/20");
        assertThat(text.get()).isEqualTo("4/20");
        assertThat(text.toString()).isEqualTo("Computed[4/20]");
        watching.cancel();
        max.set(30);
        assertThat(texts).hasSize(2);
        assertThat(text.get()).isEqualTo("4/30");

        State<Boolean> useMax = State.of(false);
        Computed<Integer> either = Computed.of(() -> useMax.get() ? max.get() : hp.get());
        List<Integer> values = new ArrayList<>();
        either.subscribe(values::add);
        useMax.set(true);
        max.set(31);
        assertThat(values).containsExactly(30, 31);

        Observable<String> mapped = hp.map(v -> "hp " + v);
        assertThat(mapped.get()).isEqualTo("hp 4");
        Observable<String> fixed = Observable.constant("x");
        assertThat(fixed.get()).isEqualTo("x");
        assertThat(fixed.subscribe(v -> {}).isActive()).isFalse();
    }

    @Test
    void listStatesReportEachChange() {
        ListState<String> list = ListState.of(List.of("a", "b"));
        List<ListState.Change<String>> changes = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        list.subscribeChanges(changes::add);
        list.subscribe(l -> sizes.add(l.size()));
        list.add("c");
        list.add(0, "z");
        assertThat(list.set(1, "A")).isEqualTo("a");
        assertThat(list.remove(0)).isEqualTo("z");
        assertThat(list.removeValue("missing")).isFalse();
        assertThat(list.removeValue("b")).isTrue();
        list.swap(0, 1);
        list.swap(1, 1);
        assertThat(list.get()).containsExactly("c", "A");
        assertThat(list.get(0)).isEqualTo("c");
        assertThat(list.size()).isEqualTo(2);
        list.clear();
        assertThat(changes)
                .extracting(ListState.Change::kind)
                .containsExactly(
                        ListState.Kind.ADD,
                        ListState.Kind.ADD,
                        ListState.Kind.SET,
                        ListState.Kind.REMOVE,
                        ListState.Kind.REMOVE,
                        ListState.Kind.SET,
                        ListState.Kind.SET,
                        ListState.Kind.RESET);
        assertThat(sizes.get(sizes.size() - 1)).isZero();
        assertThat(ListState.<String>of().toString()).isEqualTo("ListState[]");
    }

    @Test
    void bindingsFollowTheNodeOnAndOffScreen() {
        startUi();
        State<Integer> coins = State.of(3);
        Label label = label(coins.map(c -> "Coins: " + c));
        assertThat(label.text().plain()).isEqualTo("Coins: 3");
        ui.hud().add(game, label.anchor(Anchor.TOP_LEFT).offset(8f, 8f));
        step(1);
        coins.set(12);
        assertThat(label.text().plain()).isEqualTo("Coins: 12");
        float wide = label.width();
        coins.set(1200);
        step(1);
        assertThat(label.width()).isGreaterThan(wide);
        assertThat(label.x()).isEqualTo(8f);

        assertThat(ui.hud().remove(label)).isTrue();
        assertThat(ui.hud().remove(label)).isFalse();
        coins.set(5);
        assertThat(label.text().plain()).isEqualTo("Coins: 1200");
        assertThat(label.isDisposed()).isTrue();
        ui.hud().add(game, label);
        assertThat(label.text().plain()).isEqualTo("Coins: 5");
        assertThat(ui.hud().nodes()).containsExactly(label);
        ui.hud().setVisible(false);
        assertThat(ui.hud().isVisible()).isFalse();
        step(1);
        ui.hud().setVisible(true);
    }
}
