package dev.gulp.api.anim;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityEvent;

/**
 * An {@link Animator} finished an animation that ends by itself ({@link PlayMode#ONCE}, {@link PlayMode#REVERSED} or
 * {@code playOnce}).
 *
 * <pre>{@code
 * @EventHandler
 * void onEnd(AnimationEndEvent event) {
 *     if (event.animation().equals("die")) event.entity().remove();
 * }
 * }</pre>
 */
public final class AnimationEndEvent extends EntityEvent {

    private final String animation;

    /**
     * Creates the event.
     *
     * @param entity the animated entity
     * @param animation the name of the animation
     */
    public AnimationEndEvent(Entity entity, String animation) {
        super(entity);
        this.animation = animation;
    }

    /**
     * Returns the animation that ended.
     *
     * @return its name
     */
    public String animation() {
        return animation;
    }
}
