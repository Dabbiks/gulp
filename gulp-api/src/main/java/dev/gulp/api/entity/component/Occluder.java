package dev.gulp.api.entity.component;

import dev.gulp.api.entity.Component;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.physics.Shape;
import org.jspecify.annotations.Nullable;

/**
 * Makes its entity cast shadows from the lights of the world. Without a shape it blocks light with a box of the
 * entity's size; the shape rotates with the entity.
 *
 * <pre>{@code
 * EntityType.builder(key("pillar")).size(1, 3).component(Occluder::new).build();
 * }</pre>
 */
public final class Occluder extends Component {

    private @Nullable Shape shape;
    private Vec2 offset = Vec2.ZERO;

    /** Creates an occluder shaped like the entity. */
    public Occluder() {}

    /**
     * Creates an occluder with a shape.
     *
     * @param shape the shape, relative to the entity position
     */
    public Occluder(Shape shape) {
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
     * Moves the shape relative to the entity.
     *
     * @param x world units
     * @param y world units
     * @return this component
     */
    public Occluder offset(float x, float y) {
        offset = new Vec2(x, y);
        return this;
    }

    /**
     * Returns the offset of the shape.
     *
     * @return world units
     */
    public Vec2 offset() {
        return offset;
    }
}
