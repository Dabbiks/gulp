package dev.gulp.core.event;

import dev.gulp.api.Owner;
import dev.gulp.api.event.Cancellable;
import dev.gulp.api.event.Event;
import dev.gulp.api.event.EventPriority;
import dev.gulp.api.event.Events;
import dev.gulp.api.event.HandlerList;
import dev.gulp.api.event.Listener;
import dev.gulp.api.event.Subscription;
import dev.gulp.api.event.TargetedEvent;
import dev.gulp.api.spi.ListenerHandlers;
import dev.gulp.core.CoreContext;
import dev.gulp.platform.PlatformModules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * {@link Events} implementation.
 *
 * <p>Handler arrays are built per concrete event class (including handlers of its base classes), sorted by priority and
 * cached until the next registration change, so a call does not allocate. A call works on the array it started with:
 * registrations changed during a call apply from the next call.
 */
public final class EventBus implements Events {

    /** Maximum nesting of {@link #call(Event)}. */
    public static final int MAX_DEPTH = 64;

    private static final Handler[] NONE = new Handler[0];
    private static final EventPriority[] PRIORITIES = EventPriority.values();

    private final CoreContext context;
    private final PlatformModules generated;

    private final List<Handler> global = new ArrayList<>();
    private final Map<Class<?>, Handler[]> baked = new HashMap<>();
    private final IdentityHashMap<Object, Handler[]> byTarget = new IdentityHashMap<>();
    private final Map<Class<?>, Integer> targetedTypes = new HashMap<>();
    private final Class<?>[] callStack = new Class<?>[MAX_DEPTH];
    private int depth;

    /**
     * Creates the bus.
     *
     * @param context owner state and loggers
     * @param generated source of generated listener dispatchers
     */
    public EventBus(CoreContext context, PlatformModules generated) {
        this.context = context;
        this.generated = generated;
    }

    /** One registered handler. Immutable except for the subscription state. */
    static final class Handler {
        final Class<?> type;
        final int priority;
        final boolean ignoreCancelled;
        final Consumer<Object> action;
        final Owner owner;
        final @Nullable Object target;
        final SubscriptionImpl subscription;

        @SuppressWarnings("unchecked")
        Handler(
                Class<?> type,
                EventPriority priority,
                boolean ignoreCancelled,
                Consumer<?> action,
                Owner owner,
                @Nullable Object target,
                SubscriptionImpl subscription) {
            this.type = type;
            this.priority = priority.ordinal();
            this.ignoreCancelled = ignoreCancelled;
            this.action = (Consumer<Object>) action;
            this.owner = owner;
            this.target = target;
            this.subscription = subscription;
        }
    }

    /** Subscription covering one or more handlers. */
    final class SubscriptionImpl implements Subscription {
        final List<Handler> handlers = new ArrayList<>();
        final Owner owner;
        boolean active = true;

        SubscriptionImpl(Owner owner) {
            this.owner = owner;
        }

        @Override
        public void cancel() {
            if (active) {
                active = false;
                removeHandlers(handlers);
            }
        }

        @Override
        public boolean isActive() {
            return active;
        }
    }

    @Override
    public <E extends Event> E call(E event) {
        context.checkMainThread("Events.call");
        Class<?> type = event.getClass();
        if (depth >= MAX_DEPTH) {
            StringBuilder chain = new StringBuilder();
            for (int i = 0; i < depth; i++) {
                chain.append(callStack[i].getSimpleName()).append(" -> ");
            }
            chain.append(type.getSimpleName());
            throw new IllegalStateException("Events nested deeper than " + MAX_DEPTH + " calls: " + chain);
        }
        Handler[] handlers = bake(type);
        Handler[] targeted = event instanceof TargetedEvent t ? byTarget.get(t.target()) : null;
        if (handlers.length == 0 && targeted == null) {
            return event;
        }
        callStack[depth++] = type;
        try {
            int index = 0;
            for (int priority = 0; priority < PRIORITIES.length; priority++) {
                while (index < handlers.length && handlers[index].priority == priority) {
                    invoke(handlers[index++], event);
                }
                if (targeted != null) {
                    for (Handler handler : targeted) {
                        if (handler.priority == priority && handler.type.isInstance(event)) {
                            invoke(handler, event);
                        }
                    }
                }
            }
        } finally {
            callStack[--depth] = null;
        }
        return event;
    }

    private void invoke(Handler handler, Event event) {
        Cancellable cancellable = event instanceof Cancellable c ? c : null;
        boolean cancelledBefore = cancellable != null && cancellable.isCancelled();
        if (handler.ignoreCancelled && cancelledBefore) {
            return;
        }
        try {
            handler.action.accept(event);
        } catch (Throwable error) {
            context.loggerOf(handler.owner)
                    .error(
                            "Unhandled exception in handler of " + event.eventName() + " (" + handler.owner.id() + ")",
                            error);
        }
        if (cancellable != null
                && handler.priority == EventPriority.MONITOR.ordinal()
                && cancellable.isCancelled() != cancelledBefore) {
            cancellable.setCancelled(cancelledBefore);
            context.loggerOf(handler.owner)
                    .warn("A MONITOR handler of " + event.eventName() + " changed its cancellation; the change was"
                            + " undone. Use HIGHEST to decide cancellation.");
        }
    }

    @Override
    public boolean hasListeners(Class<? extends Event> type) {
        if (bake(type).length > 0) {
            return true;
        }
        if (!targetedTypes.isEmpty()) {
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                if (targetedTypes.containsKey(c)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public HandlerList handlers(Class<? extends Event> type) {
        return new HandlerList() {
            @Override
            public int size() {
                return bake(type).length;
            }

            @Override
            public int size(EventPriority priority) {
                int count = 0;
                for (Handler handler : bake(type)) {
                    if (handler.priority == priority.ordinal()) {
                        count++;
                    }
                }
                return count;
            }
        };
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Subscription register(Listener listener, Owner owner) {
        context.checkMainThread("Events.register");
        context.requireActive(owner, "listener " + listener.getClass().getName());
        SubscriptionImpl subscription = new SubscriptionImpl(owner);
        boolean found = false;
        for (Class<?> c = listener.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            ListenerHandlers handlers = generated.lookup(ListenerHandlers.class, c);
            if (handlers == null) {
                continue;
            }
            found = true;
            handlers.register(listener, new ListenerHandlers.HandlerSink() {
                @Override
                public <E extends Event> void handler(
                        Class<E> type, EventPriority priority, boolean ignoreCancelled, Consumer<? super E> handler) {
                    subscription.handlers.add(
                            new Handler(type, priority, ignoreCancelled, handler, owner, null, subscription));
                }
            });
        }
        if (!found) {
            throw new IllegalStateException("No generated dispatcher for listener "
                    + listener.getClass().getName()
                    + ". Annotate its methods with @EventHandler and add gulp-processor as an annotation processor"
                    + " (annotationProcessor(\"dev.gulp:gulp-processor\")).");
        }
        global.addAll(subscription.handlers);
        invalidate();
        return subscription;
    }

    @Override
    public <E extends Event> Subscription on(
            Class<E> type, EventPriority priority, boolean ignoreCancelled, Consumer<? super E> handler, Owner owner) {
        context.checkMainThread("Events.on");
        context.requireActive(owner, "a handler of " + type.getSimpleName());
        SubscriptionImpl subscription = new SubscriptionImpl(owner);
        Handler registered = new Handler(type, priority, ignoreCancelled, handler, owner, null, subscription);
        subscription.handlers.add(registered);
        global.add(registered);
        invalidate();
        return subscription;
    }

    @Override
    public <E extends Event> Subscription on(
            Class<E> type, Object target, EventPriority priority, Consumer<? super E> handler, Owner owner) {
        context.checkMainThread("Events.on");
        context.requireActive(owner, "a handler of " + type.getSimpleName());
        if (!TargetedEvent.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException(
                    type.getName() + " does not implement TargetedEvent, so it cannot be bound to a target");
        }
        SubscriptionImpl subscription = new SubscriptionImpl(owner);
        Handler registered = new Handler(type, priority, false, handler, owner, target, subscription);
        subscription.handlers.add(registered);
        Handler[] current = byTarget.getOrDefault(target, NONE);
        Handler[] updated = new Handler[current.length + 1];
        System.arraycopy(current, 0, updated, 0, current.length);
        updated[current.length] = registered;
        byTarget.put(target, updated);
        targetedTypes.merge(type, 1, Integer::sum);
        return subscription;
    }

    @Override
    public void release(Object target) {
        Handler[] handlers = byTarget.remove(target);
        if (handlers != null) {
            for (Handler handler : handlers) {
                handler.subscription.active = false;
                targetedTypes.computeIfPresent(handler.type, (k, count) -> count == 1 ? null : count - 1);
            }
        }
    }

    /**
     * Removes every handler of an owner; called when it is disabled.
     *
     * @param owner the owner
     */
    public void unregisterAll(Owner owner) {
        List<Handler> removed = new ArrayList<>();
        for (Handler handler : global) {
            if (handler.owner == owner) {
                removed.add(handler);
            }
        }
        for (Handler[] handlers : byTarget.values()) {
            for (Handler handler : handlers) {
                if (handler.owner == owner) {
                    removed.add(handler);
                }
            }
        }
        for (Handler handler : removed) {
            handler.subscription.active = false;
        }
        removeHandlers(removed);
    }

    /**
     * Returns the number of handlers registered by an owner, for diagnostics.
     *
     * @param owner the owner
     * @return the handler count
     */
    public int countOwnedBy(Owner owner) {
        int count = 0;
        for (Handler handler : global) {
            if (handler.owner == owner) {
                count++;
            }
        }
        for (Handler[] handlers : byTarget.values()) {
            for (Handler handler : handlers) {
                if (handler.owner == owner) {
                    count++;
                }
            }
        }
        return count;
    }

    private void removeHandlers(List<Handler> handlers) {
        boolean changedGlobal = false;
        for (Handler handler : handlers) {
            if (handler.target == null) {
                changedGlobal |= global.remove(handler);
            } else {
                Handler[] current = byTarget.get(handler.target);
                if (current == null) {
                    continue;
                }
                List<Handler> kept = new ArrayList<>();
                for (Handler h : current) {
                    if (h != handler) {
                        kept.add(h);
                    }
                }
                if (kept.isEmpty()) {
                    byTarget.remove(handler.target);
                } else {
                    byTarget.put(handler.target, kept.toArray(NONE));
                }
                targetedTypes.computeIfPresent(handler.type, (k, count) -> count == 1 ? null : count - 1);
            }
        }
        if (changedGlobal) {
            invalidate();
        }
    }

    private void invalidate() {
        baked.clear();
    }

    private Handler[] bake(Class<?> type) {
        Handler[] cached = baked.get(type);
        if (cached != null) {
            return cached;
        }
        List<Handler> matching = new ArrayList<>();
        for (Handler handler : global) {
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                if (c == handler.type) {
                    matching.add(handler);
                    break;
                }
            }
        }
        // Stable sort by priority keeps registration order within a priority.
        matching.sort((a, b) -> Integer.compare(a.priority, b.priority));
        Handler[] result = matching.isEmpty() ? NONE : matching.toArray(NONE);
        baked.put(type, result);
        return result;
    }
}
