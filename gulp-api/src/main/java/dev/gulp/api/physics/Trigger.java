package dev.gulp.api.physics;

import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.spi.PhysicsAccess;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * An area that notices entities without stopping them: {@link TriggerEnterEvent} when an entity with a {@link
 * Collider}, {@link Mover} or {@link Body} starts overlapping it, {@link TriggerExitEvent} when it leaves. An entity
 * counts when the trigger's mask contains its layer and its mask contains the trigger's layer. A disabled trigger
 * reports nothing and forgets who was inside.
 *
 * <pre>{@code
 * coin.add(new Trigger().layer(PICKUP));
 * on(TriggerEnterEvent.class, e -> {
 *     if (e.other().tags().has("player")) { e.entity().remove(); }
 * });
 * }</pre>
 */
public final class Trigger extends Component {

    private @Nullable Shape shape;
    private Vec2 offset = Vec2.ZERO;
    private CollisionLayer layer = CollisionLayer.DEFAULT;
    private CollisionMask mask = CollisionMask.ALL;

    /** Creates a trigger shaped like the entity, or like its {@link Collider}. */
    public Trigger() {}

    /**
     * Creates a trigger with its own shape.
     *
     * @param shape the shape, relative to the entity position
     */
    public Trigger(Shape shape) {
        this.shape = shape;
    }

    /**
     * Returns the shape.
     *
     * @return the shape, or {@code null} to use the collider's or the entity's size
     */
    public @Nullable Shape shape() {
        return shape;
    }

    /**
     * Changes the shape.
     *
     * @param value the shape, or {@code null}
     * @return this trigger
     */
    public Trigger shape(@Nullable Shape value) {
        shape = value;
        return this;
    }

    /**
     * Returns the offset of the shape from the entity position.
     *
     * @return the offset
     */
    public Vec2 offset() {
        return offset;
    }

    /**
     * Moves the shape relative to the entity position.
     *
     * @param x offset x
     * @param y offset y
     * @return this trigger
     */
    public Trigger offset(float x, float y) {
        offset = new Vec2(x, y);
        return this;
    }

    /**
     * Returns the layer.
     *
     * @return the layer
     */
    public CollisionLayer layer() {
        return layer;
    }

    /**
     * Sets the layer.
     *
     * @param value the layer
     * @return this trigger
     */
    public Trigger layer(CollisionLayer value) {
        layer = value;
        return this;
    }

    /**
     * Returns the layers of entities this trigger notices.
     *
     * @return the mask
     */
    public CollisionMask mask() {
        return mask;
    }

    /**
     * Sets the layers of entities this trigger notices.
     *
     * @param value the mask
     * @return this trigger
     */
    public Trigger mask(CollisionMask value) {
        mask = value;
        return this;
    }

    /**
     * Notices only entities on the given layers.
     *
     * @param layers the layers
     * @return this trigger
     */
    public Trigger collidesWith(CollisionLayer... layers) {
        mask = CollisionMask.of(layers);
        return this;
    }

    /**
     * Returns the entities inside, as of the last tick.
     *
     * @return the entities
     */
    public List<Entity> triggered() {
        return isAttached() && entity().isSpawned() ? PhysicsAccess.backend().triggered(this) : List.of();
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
