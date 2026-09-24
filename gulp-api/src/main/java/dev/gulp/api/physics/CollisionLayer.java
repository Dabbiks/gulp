package dev.gulp.api.physics;

import dev.gulp.api.registry.Key;
import dev.gulp.api.registry.Keyed;
import dev.gulp.api.spi.PhysicsAccess;

/**
 * A named collision layer. Layers are registered in {@code Registries.COLLISION_LAYER} (at most {@value #MAX}) and
 * filter contacts, triggers and queries: two colliders touch when each one's mask contains the other's layer and the
 * world's matrix allows the pair ({@link Physics#setCollides}).
 *
 * <pre>{@code
 * PICKUP = registries().register(Registries.COLLISION_LAYER, CollisionLayer.of(key("pickup")));
 * coin.add(new Trigger().layer(PICKUP));
 * }</pre>
 */
public final class CollisionLayer implements Keyed {

    /** Most layers a game can have, engine layers included. */
    public static final int MAX = 32;

    /** Layer of colliders that do not choose one. */
    public static final CollisionLayer DEFAULT = new CollisionLayer(Key.of(Key.RESERVED, "default"));

    /** Layer of the collision tiles of tile maps. */
    public static final CollisionLayer TILES = new CollisionLayer(Key.of(Key.RESERVED, "tiles"));

    static {
        PhysicsAccess.installLayers((layer, bit) -> layer.bit = bit);
    }

    private final Key key;
    private int bit = -1;

    private CollisionLayer(Key key) {
        this.key = key;
    }

    /**
     * Creates a layer to register.
     *
     * @param key the layer key
     * @return the layer
     */
    public static CollisionLayer of(Key key) {
        return new CollisionLayer(key);
    }

    @Override
    public Key key() {
        return key;
    }

    /**
     * Returns the bit of this layer in masks, assigned when registries freeze.
     *
     * @return {@code 0..31}, or {@code -1} before registration
     */
    public int bit() {
        return bit;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CollisionLayer layer && layer.key.equals(key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return "CollisionLayer[" + key + "]";
    }
}
