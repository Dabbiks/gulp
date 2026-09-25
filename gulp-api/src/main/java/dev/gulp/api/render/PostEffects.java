package dev.gulp.api.render;

import java.util.List;

/**
 * A chain of post-processing effects, applied in the order added. {@code world.postEffects()} changes the world as seen
 * by its cameras (not the UI); {@code display().postEffects()} changes the whole frame.
 *
 * <pre>{@code
 * world.postEffects().add(new Bloom().threshold(0.6f));
 * display().postEffects().add(new Crt());
 * }</pre>
 */
public interface PostEffects {

    /**
     * Appends an effect.
     *
     * @param effect the effect
     * @param <E> the effect type
     * @return the same effect
     */
    <E extends PostEffect> E add(E effect);

    /**
     * Removes an effect.
     *
     * @param effect the effect
     * @return {@code true} if it was in the chain
     */
    boolean remove(PostEffect effect);

    /** Removes all effects. */
    void clear();

    /**
     * Returns the effects in order.
     *
     * @return the effects
     */
    List<PostEffect> effects();
}
