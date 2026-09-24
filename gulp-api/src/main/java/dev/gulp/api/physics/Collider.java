package dev.gulp.api.physics;

import dev.gulp.api.entity.Component;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.spi.PhysicsAccess;
import org.jspecify.annotations.Nullable;

/**
 * Gives an entity a solid shape. Alone it is a static obstacle that movers and bodies bump into; together with a
 * {@link Mover}, {@link Trigger} or {@link Body} it provides their shape, layer and mask. Without a shape it uses a box
 * of the entity's size.
 *
 * <pre>{@code
 * EntityType.builder(key("crate"))
 *         .size(1, 1)
 *         .component(() -> new Collider().layer(WORLD).collidesWith(PLAYER, ENEMY))
 *         .build();
 * }</pre>
 */
public final class Collider extends Component {

    private @Nullable Shape shape;
    private Vec2 offset = Vec2.ZERO;
    private CollisionLayer layer = CollisionLayer.DEFAULT;
    private CollisionMask mask = CollisionMask.ALL;
    private boolean oneWay;

    /** Creates a collider shaped like the entity. */
    public Collider() {}

    /**
     * Creates a collider with a shape.
     *
     * @param shape the shape, relative to the entity position
     */
    public Collider(Shape shape) {
        this.shape = shape;
    }

    /**
     * Returns the shape.
     *
     * @return the shape, or {@code null} for a box of the entity's size
     */
    public @Nullable Shape shape() {
        return shape;
    }

    /**
     * Changes the shape.
     *
     * @param value the shape, or {@code null} for a box of the entity's size
     * @return this collider
     */
    public Collider shape(@Nullable Shape value) {
        shape = value;
        return this;
    }

    /**
     * Returns the offset of the shape from the entity position.
     *
     * @return the offset in world units
     */
    public Vec2 offset() {
        return offset;
    }

    /**
     * Moves the shape relative to the entity position.
     *
     * @param x offset x
     * @param y offset y
     * @return this collider
     */
    public Collider offset(float x, float y) {
        offset = new Vec2(x, y);
        return this;
    }

    /**
     * Returns the layer.
     *
     * @return the layer, {@link CollisionLayer#DEFAULT} by default
     */
    public CollisionLayer layer() {
        return layer;
    }

    /**
     * Sets the layer.
     *
     * @param value the layer
     * @return this collider
     */
    public Collider layer(CollisionLayer value) {
        layer = value;
        return this;
    }

    /**
     * Returns the layers this collider touches.
     *
     * @return the mask, {@link CollisionMask#ALL} by default
     */
    public CollisionMask mask() {
        return mask;
    }

    /**
     * Sets the layers this collider touches.
     *
     * @param value the mask
     * @return this collider
     */
    public Collider mask(CollisionMask value) {
        mask = value;
        return this;
    }

    /**
     * Touches only the given layers.
     *
     * @param layers the layers
     * @return this collider
     */
    public Collider collidesWith(CollisionLayer... layers) {
        mask = CollisionMask.of(layers);
        return this;
    }

    /**
     * Returns whether the collider blocks only from above, like a platform you can jump through.
     *
     * @return {@code true} for a one-way platform
     */
    public boolean isOneWay() {
        return oneWay;
    }

    /**
     * Makes the collider block only things falling onto it from above.
     *
     * @param value whether one-way
     * @return this collider
     */
    public Collider oneWay(boolean value) {
        oneWay = value;
        return this;
    }

    @Override
    protected void onSpawn() {
        PhysicsAccess.backend().attach(this);
    }

    @Override
    protected void onRemove() {
        PhysicsAccess.backend().detach(this);
    }
}
