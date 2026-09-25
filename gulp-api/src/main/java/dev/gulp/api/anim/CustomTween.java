package dev.gulp.api.anim;

import org.jspecify.annotations.Nullable;

/** Hands the eased time to a function. */
final class CustomTween extends Tween {

    private final @Nullable Object target;
    private final float seconds;
    private final TweenFunction function;

    CustomTween(@Nullable Object target, float seconds, TweenFunction function) {
        this.target = target;
        this.seconds = Math.max(0f, seconds);
        this.function = function;
    }

    @Override
    public float duration() {
        return seconds;
    }

    @Override
    void apply(float t) {
        function.apply(t);
    }

    @Override
    @Nullable Object target() {
        return target;
    }
}
