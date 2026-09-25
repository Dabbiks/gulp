package dev.gulp.api.anim;

import org.jspecify.annotations.Nullable;

/** Tweens one property of one target: {@code to}, {@code from}, {@code by} or between two given values. */
final class ValueTween<T, V> extends Tween {

    enum Mode {
        TO,
        FROM,
        BY,
        FROM_TO
    }

    private final T target;
    private final Property<T, V> property;
    private final Mode mode;
    private final V value;
    private final @Nullable V end;
    private final float seconds;
    private @Nullable V from;
    private @Nullable V to;

    ValueTween(T target, Property<T, V> property, Mode mode, V value, @Nullable V end, float seconds) {
        this.target = target;
        this.property = property;
        this.mode = mode;
        this.value = value;
        this.end = end;
        this.seconds = Math.max(0f, seconds);
    }

    @Override
    public float duration() {
        return seconds;
    }

    @Override
    void reset() {
        from = null;
        to = null;
    }

    @Override
    void begin() {
        V current = property.get(target);
        switch (mode) {
            case TO -> {
                from = current;
                to = value;
            }
            case FROM -> {
                from = value;
                to = current;
            }
            case BY -> {
                from = current;
                to = property.interpolator().add(current, value);
            }
            case FROM_TO -> {
                from = value;
                to = end;
            }
        }
    }

    @Override
    void apply(float t) {
        V a = from;
        V b = to;
        if (a != null && b != null) {
            property.set(target, property.interpolator().interpolate(a, b, t));
        }
    }

    @Override
    Object target() {
        return target;
    }
}
