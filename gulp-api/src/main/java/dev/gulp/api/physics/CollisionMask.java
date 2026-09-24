package dev.gulp.api.physics;

import java.util.ArrayList;
import java.util.List;

/**
 * A set of collision layers, used as the "collides with" filter of colliders, triggers and queries.
 *
 * <pre>{@code
 * RayHit hit = world.physics().raycast(eye, target, CollisionMask.of(CollisionLayer.TILES, WALLS));
 * CollisionMask notPlayer = CollisionMask.ALL.without(PLAYER);
 * }</pre>
 */
public final class CollisionMask {

    /** Every layer. */
    public static final CollisionMask ALL = new CollisionMask(true, List.of());

    /** No layer. */
    public static final CollisionMask NONE = new CollisionMask(false, List.of());

    private final boolean all;
    private final List<CollisionLayer> layers;

    private CollisionMask(boolean all, List<CollisionLayer> layers) {
        this.all = all;
        this.layers = List.copyOf(layers);
    }

    /**
     * Returns a mask of the given layers.
     *
     * @param layers the layers
     * @return the mask
     */
    public static CollisionMask of(CollisionLayer... layers) {
        return new CollisionMask(false, List.of(layers));
    }

    /**
     * Returns this mask with one more layer.
     *
     * @param layer the layer to add
     * @return a new mask
     */
    public CollisionMask with(CollisionLayer layer) {
        List<CollisionLayer> next = new ArrayList<>(layers);
        if (all) {
            next.remove(layer);
        } else if (!next.contains(layer)) {
            next.add(layer);
        }
        return new CollisionMask(all, next);
    }

    /**
     * Returns this mask without a layer.
     *
     * @param layer the layer to remove
     * @return a new mask
     */
    public CollisionMask without(CollisionLayer layer) {
        List<CollisionLayer> next = new ArrayList<>(layers);
        if (all) {
            if (!next.contains(layer)) {
                next.add(layer);
            }
        } else {
            next.remove(layer);
        }
        return new CollisionMask(all, next);
    }

    /**
     * Returns whether a layer is in the mask.
     *
     * @param layer the layer
     * @return {@code true} if contained
     */
    public boolean contains(CollisionLayer layer) {
        return all != layers.contains(layer);
    }

    /**
     * Returns the mask as bits of registered layers.
     *
     * @return one bit per contained layer; unregistered layers are ignored
     */
    public int bits() {
        int bits = 0;
        for (CollisionLayer layer : layers) {
            if (layer.bit() >= 0) {
                bits |= 1 << layer.bit();
            }
        }
        return all ? ~bits : bits;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CollisionMask mask && mask.all == all && mask.layers.equals(layers);
    }

    @Override
    public int hashCode() {
        return layers.hashCode() * 31 + (all ? 1 : 0);
    }

    @Override
    public String toString() {
        return all
                ? (layers.isEmpty() ? "CollisionMask[all]" : "CollisionMask[all except " + layers + "]")
                : "CollisionMask" + layers;
    }
}
