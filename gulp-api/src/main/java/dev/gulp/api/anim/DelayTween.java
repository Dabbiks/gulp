package dev.gulp.api.anim;

import org.jspecify.annotations.Nullable;

/** Waits, or runs code once. */
final class DelayTween extends Tween {

    private final float seconds;
    private final @Nullable Runnable action;

    DelayTween(float seconds, @Nullable Runnable action) {
        this.seconds = Math.max(0f, seconds);
        this.action = action;
    }

    @Override
    public float duration() {
        return seconds;
    }

    @Override
    void begin() {
        if (action != null) {
            action.run();
        }
    }
}
