package dev.gulp.api.spi;

import dev.gulp.api.Owner;
import dev.gulp.api.anim.Animation;
import org.jspecify.annotations.Nullable;

/**
 * Lets the engine run tweens and timelines, whose stepping methods are hidden from game code. For {@code gulp-core}
 * only.
 *
 * <pre>{@code
 * AnimationAccess.installBackend(animationSystem);
 * boolean done = AnimationAccess.advance(tween, gameSeconds, realSeconds);
 * }</pre>
 */
public final class AnimationAccess {

    /** Implemented by the engine: keeps started animations and advances them every frame. */
    public interface Backend {
        /**
         * Starts running an animation.
         *
         * @param animation the tween or timeline
         */
        void start(Animation animation);

        /**
         * Kills every running animation that involves a target.
         *
         * @param target the target
         * @return how many were killed
         */
        int killAll(Object target);
    }

    /** Implemented inside {@link Animation}. */
    public interface Hooks {
        /**
         * Advances an animation.
         *
         * @param animation the animation
         * @param gameSeconds time passed in game time (scaled, zero while paused)
         * @param realSeconds time passed in real time
         * @return {@code true} when it finished or was killed
         */
        boolean advance(Animation animation, float gameSeconds, float realSeconds);

        /**
         * Returns what the animation changes, to stop it when that goes away.
         *
         * @param animation the animation
         * @return the target, or {@code null}
         */
        @Nullable Object target(Animation animation);

        /**
         * Returns whether the animation involves a target, directly or through its parts.
         *
         * @param animation the animation
         * @param target the target
         * @return {@code true} if it animates the target
         */
        boolean involves(Animation animation, Object target);

        /**
         * Returns the owner the animation belongs to.
         *
         * @param animation the animation
         * @return the owner, or {@code null}
         */
        @Nullable Owner owner(Animation animation);
    }

    private static @Nullable Backend backend;
    private static @Nullable Hooks hooks;

    private AnimationAccess() {}

    /**
     * Installs the engine side.
     *
     * @param installed the backend
     */
    public static void installBackend(Backend installed) {
        backend = installed;
    }

    /**
     * Installs the hooks; called by {@link Animation}.
     *
     * @param installed the hooks
     */
    public static void installHooks(Hooks installed) {
        if (hooks == null) {
            hooks = installed;
        }
    }

    /**
     * Returns the engine side.
     *
     * @return the backend
     * @throws IllegalStateException without a running engine
     */
    public static Backend backend() {
        Backend current = backend;
        if (current == null) {
            throw new IllegalStateException("Animations run only inside a started engine");
        }
        return current;
    }

    private static Hooks hooks() {
        Hooks current = hooks;
        if (current == null) {
            throw new IllegalStateException("Animation hooks are not installed");
        }
        return current;
    }

    /**
     * Advances an animation.
     *
     * @param animation the animation; creating it installed the hooks
     * @param gameSeconds game time passed
     * @param realSeconds real time passed
     * @return {@code true} when finished
     */
    public static boolean advance(Animation animation, float gameSeconds, float realSeconds) {
        return hooks().advance(animation, gameSeconds, realSeconds);
    }

    /**
     * Returns the target of an animation.
     *
     * @param animation the animation
     * @return the target, or {@code null}
     */
    public static @Nullable Object target(Animation animation) {
        return hooks().target(animation);
    }

    /**
     * Returns whether an animation involves a target.
     *
     * @param animation the animation
     * @param target the target
     * @return {@code true} if it animates the target
     */
    public static boolean involves(Animation animation, Object target) {
        return hooks().involves(animation, target);
    }

    /**
     * Returns the owner of an animation.
     *
     * @param animation the animation
     * @return the owner, or {@code null}
     */
    public static @Nullable Owner owner(Animation animation) {
        return hooks().owner(animation);
    }
}
