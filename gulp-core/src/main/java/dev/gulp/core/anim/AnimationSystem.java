package dev.gulp.core.anim;

import dev.gulp.api.Logger;
import dev.gulp.api.Owner;
import dev.gulp.api.anim.Animation;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.spi.AnimationAccess;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs started tweens and timelines once per frame. Animations started while others run join on the next frame; one
 * whose target entity was removed or whose owner was disabled is killed. Exceptions are logged and kill only the
 * animation that threw.
 */
public final class AnimationSystem implements AnimationAccess.Backend {

    private final Logger logger;
    private final List<Animation> active = new ArrayList<>();
    private final List<Animation> added = new ArrayList<>();

    /**
     * Creates the system.
     *
     * @param logger where failures go
     */
    public AnimationSystem(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void start(Animation animation) {
        added.add(animation);
    }

    @Override
    public int killAll(Object target) {
        int killed = 0;
        killed += killIn(active, target);
        killed += killIn(added, target);
        return killed;
    }

    private static int killIn(List<Animation> list, Object target) {
        int killed = 0;
        for (int i = 0; i < list.size(); i++) {
            Animation animation = list.get(i);
            if (animation.isRunning() && AnimationAccess.involves(animation, target)) {
                animation.kill();
                killed++;
            }
        }
        return killed;
    }

    /**
     * Advances every running animation.
     *
     * @param gameSeconds game time passed (scaled, zero while paused)
     * @param realSeconds real time passed
     */
    public void frame(float gameSeconds, float realSeconds) {
        if (!added.isEmpty()) {
            active.addAll(added);
            added.clear();
        }
        int kept = 0;
        for (int i = 0; i < active.size(); i++) {
            Animation animation = active.get(i);
            boolean done;
            try {
                if (orphaned(animation)) {
                    animation.kill();
                }
                done = AnimationAccess.advance(animation, gameSeconds, realSeconds);
            } catch (RuntimeException error) {
                logger.error("An animation failed and was stopped", error);
                animation.kill();
                AnimationAccess.advance(animation, 0f, 0f);
                done = true;
            }
            if (!done) {
                active.set(kept++, animation);
            }
        }
        for (int i = active.size() - 1; i >= kept; i--) {
            active.remove(i);
        }
    }

    private static boolean orphaned(Animation animation) {
        Owner owner = animation.owner();
        if (owner != null && !owner.isEnabled()) {
            return true;
        }
        return AnimationAccess.target(animation) instanceof Entity entity && entity.isRemoved();
    }

    /**
     * Returns the animations that run.
     *
     * @return the count, including those starting next frame
     */
    public int size() {
        return active.size() + added.size();
    }

    /** Kills everything, when the engine stops. */
    public void clear() {
        // A killed animation that advances once forgets it was scheduled, so a later engine can start it again.
        for (Animation animation : active) {
            animation.kill();
            AnimationAccess.advance(animation, 0f, 0f);
        }
        for (Animation animation : added) {
            animation.kill();
            AnimationAccess.advance(animation, 0f, 0f);
        }
        active.clear();
        added.clear();
    }
}
