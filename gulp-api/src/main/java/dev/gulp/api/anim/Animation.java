package dev.gulp.api.anim;

import dev.gulp.api.Owner;
import dev.gulp.api.spi.AnimationAccess;
import org.jspecify.annotations.Nullable;

/**
 * Something the engine plays over time: a {@link Tween} or a {@link Timeline}. Animations run every frame in game time
 * (scaled, stopped while paused) unless {@code realtime}. They stop by themselves when their target entity is removed
 * or their owner is disabled.
 *
 * <pre>{@code
 * Animation glow = Tweens.pulse(lamp, 0.1f, 1f).owner(this).start();
 * glow.pause();
 * }</pre>
 */
public abstract sealed class Animation permits Tween, Timeline {

    static {
        AnimationAccess.installHooks(new AnimationAccess.Hooks() {
            @Override
            public boolean advance(Animation animation, float gameSeconds, float realSeconds) {
                if (animation.killed) {
                    animation.scheduled = false;
                    return true;
                }
                if (animation.paused) {
                    return false;
                }
                boolean done = animation.step(animation.realtime ? realSeconds : gameSeconds);
                if (done) {
                    animation.running = false;
                    animation.scheduled = false;
                }
                return done;
            }

            @Override
            public @Nullable Object target(Animation animation) {
                return animation.target();
            }

            @Override
            public boolean involves(Animation animation, Object target) {
                return animation.involves(target);
            }

            @Override
            public @Nullable Owner owner(Animation animation) {
                return animation.owner;
            }
        });
    }

    private @Nullable Owner owner;
    boolean realtime;
    private boolean paused;
    private boolean running;
    private boolean killed;
    private boolean scheduled;

    Animation() {}

    /**
     * Ties the animation to an owner: it stops when the owner (a module, for example) is disabled.
     *
     * @param value the owner
     * @return this animation
     */
    public Animation owner(Owner value) {
        owner = value;
        return this;
    }

    /**
     * Returns the owner.
     *
     * @return the owner, or {@code null}
     */
    public @Nullable Owner owner() {
        return owner;
    }

    /**
     * Starts playing from the beginning.
     *
     * @return this animation
     */
    public Animation start() {
        restart();
        killed = false;
        paused = false;
        running = true;
        if (!scheduled) {
            scheduled = true;
            AnimationAccess.backend().start(this);
        }
        return this;
    }

    /** Stops advancing until {@link #resume()}. */
    public void pause() {
        paused = true;
    }

    /** Continues after {@link #pause()}. */
    public void resume() {
        paused = false;
    }

    /** Stops for good, leaving values where they are. */
    public void kill() {
        killed = true;
        running = false;
    }

    /**
     * Returns whether the animation is playing or paused, and not finished or killed.
     *
     * @return {@code true} while running
     */
    public boolean isRunning() {
        return running && !killed;
    }

    /**
     * Returns whether the animation is paused.
     *
     * @return {@code true} while paused
     */
    public boolean isPaused() {
        return paused;
    }

    /**
     * Returns how far the animation got.
     *
     * @return {@code 0..1}
     */
    public abstract float progress();

    /** Resets the playing state before a start. */
    abstract void restart();

    /**
     * Advances by some seconds.
     *
     * @return {@code true} when finished
     */
    abstract boolean step(float seconds);

    abstract @Nullable Object target();

    abstract boolean involves(Object target);
}
