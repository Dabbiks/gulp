package dev.gulp.api.anim;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityEvent;

/**
 * An {@link Animator} showed a new frame. Fired only while something listens to it.
 *
 * <pre>{@code
 * @EventHandler
 * void onFrame(AnimationFrameEvent event) {
 *     if (event.animation().equals("run") && event.frame() == 2) playStep(event.entity());
 * }
 * }</pre>
 */
public final class AnimationFrameEvent extends EntityEvent {

    private final String animation;
    private final int frame;

    /**
     * Creates the event.
     *
     * @param entity the animated entity
     * @param animation the name of the animation
     * @param frame the frame that showed
     */
    public AnimationFrameEvent(Entity entity, String animation, int frame) {
        super(entity);
        this.animation = animation;
        this.frame = frame;
    }

    /**
     * Returns the animation.
     *
     * @return its name
     */
    public String animation() {
        return animation;
    }

    /**
     * Returns the frame.
     *
     * @return the frame index
     */
    public int frame() {
        return frame;
    }
}
