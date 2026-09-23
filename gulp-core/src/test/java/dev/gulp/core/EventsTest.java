package dev.gulp.core;

import static dev.gulp.core.Fixtures.started;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.event.Event;
import dev.gulp.api.event.EventPriority;
import dev.gulp.api.event.Events;
import dev.gulp.api.event.Subscription;
import dev.gulp.backend.headless.HeadlessRunner;
import dev.gulp.core.Fixtures.DamageEvent;
import dev.gulp.core.Fixtures.EmptyListener;
import dev.gulp.core.Fixtures.ExtendedPingListener;
import dev.gulp.core.Fixtures.LoudPingEvent;
import dev.gulp.core.Fixtures.PingEvent;
import dev.gulp.core.Fixtures.PingListener;
import dev.gulp.core.Fixtures.TestGame;
import dev.gulp.core.Fixtures.TouchEvent;
import dev.gulp.platform.PlatformLog;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventsTest {

    private final TestGame game = new TestGame();
    private HeadlessRunner runner;
    private Events events;

    @BeforeEach
    void start() {
        runner = started(game);
        events = runner.engine().events();
    }

    @AfterEach
    void stop() {
        runner.stop();
    }

    @Test
    void generatedListenerReceivesEventsBySubclassAndPriority() {
        PingListener listener = new PingListener();
        Subscription subscription = game.listen(listener);

        PingEvent ping = events.call(new PingEvent());
        events.call(new LoudPingEvent());

        assertThat(ping.seen).containsExactly("lowest", "listener");
        assertThat(listener.calls).containsExactly("ping:PingEvent", "ping:LoudPingEvent");
        assertThat(subscription.isActive()).isTrue();
        assertThat(events.hasListeners(PingEvent.class)).isTrue();
        assertThat(events.hasListeners(LoudPingEvent.class)).isTrue();
        assertThat(events.handlers(LoudPingEvent.class).size()).isEqualTo(2);
        assertThat(events.handlers(LoudPingEvent.class).size(EventPriority.LOWEST))
                .isEqualTo(1);
        assertThat(events.handlers(LoudPingEvent.class).isEmpty()).isFalse();

        subscription.cancel();
        subscription.cancel();
        events.call(new PingEvent());
        assertThat(subscription.isActive()).isFalse();
        assertThat(listener.calls).hasSize(2);
        assertThat(events.hasListeners(PingEvent.class)).isFalse();
    }

    @Test
    void listenerSubclassesInheritHandlers() {
        ExtendedPingListener listener = new ExtendedPingListener();
        game.listen(listener);

        PingEvent ping = events.call(new PingEvent());

        assertThat(ping.seen).containsExactly("lowest", "listener", "monitor");
    }

    @Test
    void cancellationAndIgnoreCancelled() {
        PingListener listener = new PingListener();
        game.listen(listener);
        game.on(DamageEvent.class, EventPriority.LOW, e -> e.setCancelled(e.amount > 10));

        assertThat(events.call(new DamageEvent(5)).isCancelled()).isFalse();
        assertThat(events.call(new DamageEvent(50)).isCancelled()).isTrue();

        assertThat(listener.calls).containsExactly("damage:5");
    }

    @Test
    void monitorHandlersCannotChangeCancellation() {
        game.on(DamageEvent.class, EventPriority.MONITOR, e -> e.setCancelled(true));

        DamageEvent event = events.call(new DamageEvent(1));

        assertThat(event.isCancelled()).isFalse();
        assertThat(runner.backend().log().messages(PlatformLog.WARN))
                .anyMatch(m -> m.contains("MONITOR handler of DamageEvent"));
    }

    @Test
    void exceptionsAreLoggedAndOtherHandlersStillRun() {
        List<String> order = new ArrayList<>();
        game.on(PingEvent.class, EventPriority.LOW, e -> {
            throw new IllegalStateException("broken handler");
        });
        game.on(PingEvent.class, e -> order.add("after"));

        events.call(new PingEvent());

        assertThat(order).containsExactly("after");
        assertThat(runner.backend().log().entries())
                .anyMatch(entry -> entry.logger().equals("test")
                        && entry.message().contains("PingEvent")
                        && entry.error() != null);
    }

    @Test
    void registrationChangesApplyFromTheNextCall() {
        List<String> order = new ArrayList<>();
        Subscription[] second = new Subscription[1];
        game.on(PingEvent.class, EventPriority.LOW, e -> {
            order.add("first");
            if (second[0] == null) {
                second[0] = game.on(PingEvent.class, EventPriority.HIGH, x -> order.add("second"));
            } else {
                second[0].cancel();
            }
        });

        events.call(new PingEvent());
        assertThat(order).containsExactly("first");

        events.call(new PingEvent());
        assertThat(order).containsExactly("first", "first", "second");
    }

    @Test
    void nestedCallsAreLimited() {
        int[] depth = {0};
        game.on(PingEvent.class, e -> {
            depth[0]++;
            events.call(new PingEvent());
        });

        events.call(new PingEvent());

        assertThat(depth[0]).isEqualTo(64);
        assertThat(runner.backend().log().entries())
                .anyMatch(entry -> entry.error() != null
                        && entry.error().getMessage().contains("deeper than 64")
                        && entry.error().getMessage().contains("PingEvent -> PingEvent"));
    }

    @Test
    void targetedHandlersSeeOnlyTheirTarget() {
        Object door = new Object();
        Object chest = new Object();
        List<String> touched = new ArrayList<>();
        events.on(TouchEvent.class, door, EventPriority.NORMAL, e -> touched.add("door"), game);
        events.on(TouchEvent.class, door, EventPriority.LOW, e -> touched.add("door-low"), game);
        game.on(TouchEvent.class, EventPriority.HIGH, e -> touched.add("global"));

        events.call(new TouchEvent(door));
        events.call(new TouchEvent(chest));
        assertThat(touched).containsExactly("door-low", "door", "global", "global");
        assertThat(events.hasListeners(TouchEvent.class)).isTrue();

        touched.clear();
        events.release(door);
        events.release(door);
        events.call(new TouchEvent(door));
        assertThat(touched).containsExactly("global");
    }

    @Test
    void targetedSubscriptionsCanBeCancelledOneByOne() {
        Object door = new Object();
        List<String> touched = new ArrayList<>();
        Subscription first = events.on(TouchEvent.class, door, EventPriority.NORMAL, e -> touched.add("a"), game);
        Subscription second = events.on(TouchEvent.class, door, EventPriority.NORMAL, e -> touched.add("b"), game);

        first.cancel();
        events.call(new TouchEvent(door));
        second.cancel();
        events.call(new TouchEvent(door));

        assertThat(touched).containsExactly("b");
        assertThat(events.hasListeners(TouchEvent.class)).isFalse();
    }

    @Test
    void targetedHandlersRequireTargetedEvents() {
        assertThatThrownBy(() -> events.on(PingEvent.class, new Object(), EventPriority.NORMAL, e -> {}, game))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TargetedEvent");
    }

    @Test
    void listenersNeedGeneratedCode() {
        assertThatThrownBy(() -> game.listen(new EmptyListener()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gulp-processor");
    }

    @Test
    void eventsWithoutHandlersAreCheap() {
        Event event = new PingEvent();

        assertThat(events.call(event)).isSameAs(event);
        assertThat(events.hasListeners(PingEvent.class)).isFalse();
        assertThat(event.eventName()).isEqualTo("PingEvent");
    }
}
