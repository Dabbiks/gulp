package dev.gulp.api.anim;

import dev.gulp.api.Owner;
import dev.gulp.api.math.Ease;
import dev.gulp.api.math.Interpolation;
import org.jspecify.annotations.Nullable;

/**
 * A change of values over time, made with {@link Tweens}: a single property ({@code to}, {@code from}, {@code by}),
 * a pause or call, or a group played one after another or together. Settings return the tween, so it is configured in
 * one chain and ended with {@link #start()}.
 *
 * <p>{@link #ease}, {@link #yoyo()} and {@link #onUpdate} shape single tweens; {@link #delay}, {@link #repeat} and
 * {@link #onComplete} work on groups too.
 *
 * <pre>{@code
 * Tweens.to(coin, Props.POSITION, hudCoinPos, 0.4f).ease(Ease.IN_BACK)
 *         .then(Tweens.to(coin, Props.SCALE, Vec2.ZERO, 0.1f))
 *         .onComplete(coin::remove)
 *         .start();
 * }</pre>
 */
public abstract sealed class Tween extends Animation permits ValueTween, GroupTween, DelayTween, CustomTween {

    private float delay;
    private int repeat;
    private boolean yoyo;
    private Interpolation ease = Ease.OUT_QUAD;
    private @Nullable Runnable onUpdate;
    private @Nullable Runnable onComplete;

    private float delayLeft;
    private float time;
    private float played;
    private boolean begun;
    private boolean done;

    Tween() {}

    /**
     * Sets the easing curve; {@link Ease#OUT_QUAD} by default.
     *
     * @param value the curve
     * @return this tween
     */
    public Tween ease(Interpolation value) {
        ease = value;
        return this;
    }

    /**
     * Waits before starting.
     *
     * @param seconds the delay
     * @return this tween
     */
    public Tween delay(float seconds) {
        delay = Math.max(0f, seconds);
        return this;
    }

    /**
     * Plays again after the first time.
     *
     * @param times extra plays, {@code -1} forever
     * @return this tween
     */
    public Tween repeat(int times) {
        repeat = Math.max(-1, times);
        return this;
    }

    /**
     * Plays every other repeat backwards, so the value goes there and back.
     *
     * @return this tween
     */
    public Tween yoyo() {
        yoyo = true;
        return this;
    }

    /**
     * Runs code after every step.
     *
     * @param action the code
     * @return this tween
     */
    public Tween onUpdate(Runnable action) {
        onUpdate = action;
        return this;
    }

    /**
     * Runs code when the tween finishes (not when it is killed).
     *
     * @param action the code
     * @return this tween
     */
    public Tween onComplete(Runnable action) {
        onComplete = action;
        return this;
    }

    /**
     * Plays in real time, ignoring the pause and the time scale; for menus and pause screens.
     *
     * @return this tween
     */
    public Tween realtime() {
        realtime = true;
        return this;
    }

    /**
     * Returns a tween that plays this one and then another.
     *
     * @param next played after this one
     * @return the sequence; start it instead of this tween
     */
    public Tween then(Tween next) {
        return GroupTween.sequence(this, next);
    }

    @Override
    public Tween owner(Owner value) {
        super.owner(value);
        return this;
    }

    @Override
    public Tween start() {
        super.start();
        return this;
    }

    /**
     * Returns the length of one play, without delay and repeats.
     *
     * @return seconds, infinite for endless groups
     */
    public abstract float duration();

    /**
     * Returns the length of all plays with the delay.
     *
     * @return seconds, infinite when repeating forever
     */
    public final float totalDuration() {
        if (repeat < 0) {
            return Float.POSITIVE_INFINITY;
        }
        return delay + duration() * (repeat + 1);
    }

    @Override
    public float progress() {
        if (done) {
            return 1f;
        }
        float total = totalDuration();
        if (Float.isInfinite(total)) {
            return 0f;
        }
        return total <= 0f ? 0f : Math.min(1f, played / total);
    }

    @Override
    final void restart() {
        delayLeft = delay;
        time = 0f;
        played = 0f;
        begun = false;
        done = false;
        reset();
    }

    @Override
    final boolean step(float seconds) {
        return advance(seconds) >= 0f;
    }

    /**
     * Advances by some seconds.
     *
     * @return the unused seconds when this step finished the tween, or {@code -1} while it goes on
     */
    final float advance(float seconds) {
        if (done) {
            return seconds;
        }
        float left = seconds;
        if (delayLeft > 0f) {
            if (left < delayLeft) {
                delayLeft -= left;
                played += left;
                return -1f;
            }
            left -= delayLeft;
            played += delayLeft;
            delayLeft = 0f;
        }
        if (!begun) {
            begun = true;
            begin();
        }
        float rest = play(left);
        played += rest < 0f ? left : left - rest;
        if (onUpdate != null) {
            onUpdate.run();
        }
        if (rest >= 0f) {
            done = true;
            if (onComplete != null) {
                onComplete.run();
            }
        }
        return rest;
    }

    /** Plays a timed tween: eases the time of each play and hands it to {@link #apply}. */
    float play(float seconds) {
        time += seconds;
        float length = duration();
        if (repeat >= 0 && time >= length * (repeat + 1)) {
            apply(ease.apply(yoyo && repeat % 2 == 1 ? 0f : 1f));
            return time - length * (repeat + 1);
        }
        if (length <= 0f) {
            apply(ease.apply(1f));
            return -1f;
        }
        int cycle = (int) (time / length);
        float t = time / length - cycle;
        if (yoyo && cycle % 2 == 1) {
            t = 1f - t;
        }
        apply(ease.apply(t));
        return -1f;
    }

    /** Clears the playing state of subclasses. */
    void reset() {}

    /** Called once, when the delay has passed. */
    void begin() {}

    /** Shows the value at an eased point of one play. */
    void apply(float t) {}

    int repeatCount() {
        return repeat;
    }

    @Override
    @Nullable Object target() {
        return null;
    }

    @Override
    boolean involves(Object target) {
        return target() == target;
    }
}
