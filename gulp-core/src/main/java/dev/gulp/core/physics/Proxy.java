package dev.gulp.core.physics;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.physics.Collider;
import dev.gulp.api.physics.CollisionLayer;
import dev.gulp.api.physics.CollisionMask;
import dev.gulp.api.physics.Mover;
import dev.gulp.api.physics.Shape;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The solid presence of one entity in the physics: its pieces, layer, mask and cells in the broad phase. An entity with
 * a {@link Collider}, {@link Mover} or body has one proxy; the collider supplies shape and filtering to the others.
 */
final class Proxy {

    final Entity entity;
    final int serial;

    @Nullable Collider collider;

    @Nullable Mover mover;

    @Nullable BodySim body;

    @Nullable MoverState moverState;

    List<Convex> pieces = List.of();
    private @Nullable Object shapeKey;
    private float keyOffsetX = Float.NaN;
    private float keyOffsetY = Float.NaN;
    private float keyWidth = Float.NaN;
    private float keyHeight = Float.NaN;
    /** Radius around the entity origin that contains every piece. */
    float extent;

    int layerBit = 1;
    int maskBits = -1;
    CollisionLayer layer = CollisionLayer.DEFAULT;
    boolean oneWay;

    float minX;
    float minY;
    float maxX;
    float maxY;
    boolean inGrid;
    int cellMinX;
    int cellMinY;
    int cellMaxX;
    int cellMaxY;
    int stamp;
    /** Where the entity was at the last physics step, to notice things moved by game code. */
    float lastX = Float.NaN;

    float lastY = Float.NaN;

    Proxy(Entity entity, int serial) {
        this.entity = entity;
        this.serial = serial;
    }

    boolean isEmpty() {
        return collider == null && mover == null && body == null;
    }

    /** Re-reads the shape and filters from the components; rebuilds the pieces only when the shape changed. */
    void refresh() {
        Collider c = collider;
        Shape shape = null;
        float ox = 0f;
        float oy = 0f;
        BodySim b = body;
        if (b != null && b.body.shape() != null) {
            shape = b.body.shape();
        } else if (c != null) {
            shape = c.shape();
            Vec2 offset = c.offset();
            ox = offset.x();
            oy = offset.y();
        }
        Vec2 size = entity.size();
        boolean changed = shape != shapeKey
                || ox != keyOffsetX
                || oy != keyOffsetY
                || (shape == null && (size.x() != keyWidth || size.y() != keyHeight));
        if (changed) {
            shapeKey = shape;
            keyOffsetX = ox;
            keyOffsetY = oy;
            keyWidth = size.x();
            keyHeight = size.y();
            pieces = shape != null ? Shapes.pieces(shape, ox, oy) : Shapes.box(size.x(), size.y(), ox, oy);
            float e = 0f;
            for (Convex piece : pieces) {
                e = Math.max(e, piece.extent());
            }
            extent = e;
            if (b != null) {
                b.massChanged = true;
            }
        }
        if (c != null) {
            layer = c.layer();
            maskBits = c.mask().bits();
            oneWay = c.isOneWay();
        } else {
            layer = CollisionLayer.DEFAULT;
            maskBits = CollisionMask.ALL.bits();
            oneWay = false;
        }
        layerBit = layer.bit() >= 0 ? 1 << layer.bit() : 0;
    }

    /** Updates the bounds from the entity position, fattened by a margin. */
    void updateBounds(float margin) {
        float r = extent + margin;
        minX = entity.x() - r;
        minY = entity.y() - r;
        maxX = entity.x() + r;
        maxY = entity.y() + r;
    }

    /** Places piece {@code index} at the entity's position and rotation. */
    Placed place(int index, Placed out) {
        float angle = (float) Math.toRadians(entity.rotation());
        return out.set(pieces.get(index), entity.x(), entity.y(), (float) Math.cos(angle), (float) Math.sin(angle));
    }

    /** Places piece {@code index} at a given origin, unrotated. */
    Placed placeAt(int index, float x, float y, Placed out) {
        float angle = (float) Math.toRadians(entity.rotation());
        return out.set(pieces.get(index), x, y, (float) Math.cos(angle), (float) Math.sin(angle));
    }
}
