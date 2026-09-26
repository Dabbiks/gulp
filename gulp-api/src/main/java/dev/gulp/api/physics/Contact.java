package dev.gulp.api.physics;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.math.GridPos;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.spi.PhysicsAccess;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A touch found by {@link Mover#moveAndSlide}: what was hit, where, and the surface normal pointing out of it.
 *
 * <p>The engine reuses contact objects, so that moving allocates nothing: a contact is valid until the next move of
 * the same mover. Keep the values, not the object. {@link #pointX()} and the other number accessors read without
 * creating vectors.
 *
 * <pre>{@code
 * for (Contact contact : mover.moveAndSlide(velocity)) {
 *     if (contact.normalY() < -0.7f && contact.entity() != null) {
 *         stomp(contact.entity());
 *     }
 * }
 * }</pre>
 */
public final class Contact {

    static {
        PhysicsAccess.installContacts((contact, entity, tile, tileX, tileY, px, py, nx, ny) -> {
            contact.entity = entity;
            GridPos cached = contact.tile;
            if (tile != contact.hasTile || !tile || cached == null || cached.x() != tileX || cached.y() != tileY) {
                contact.tile = null;
            }
            contact.hasTile = tile;
            contact.tileX = tileX;
            contact.tileY = tileY;
            if (contact.pointX != px || contact.pointY != py) {
                contact.point = null;
            }
            contact.pointX = px;
            contact.pointY = py;
            if (contact.normalX != nx || contact.normalY != ny) {
                contact.normal = null;
            }
            contact.normalX = nx;
            contact.normalY = ny;
        });
    }

    private @Nullable Entity entity;
    private @Nullable GridPos tile;
    private boolean hasTile;
    private int tileX;
    private int tileY;
    private float pointX;
    private float pointY;
    private float normalX;
    private float normalY;
    private @Nullable Vec2 point;
    private @Nullable Vec2 normal;

    /**
     * Creates a contact.
     *
     * @param entity the entity hit, or {@code null} for a tile
     * @param tile the tile hit, or {@code null} for an entity
     * @param point where they touch, in world units
     * @param normal the unit normal pointing out of what was hit
     */
    public Contact(@Nullable Entity entity, @Nullable GridPos tile, Vec2 point, Vec2 normal) {
        this.entity = entity;
        this.tile = tile;
        this.hasTile = tile != null;
        this.tileX = tile == null ? 0 : tile.x();
        this.tileY = tile == null ? 0 : tile.y();
        this.pointX = point.x();
        this.pointY = point.y();
        this.normalX = normal.x();
        this.normalY = normal.y();
        this.point = point;
        this.normal = normal;
    }

    /**
     * Returns the entity hit.
     *
     * @return the entity, or {@code null} for a tile
     */
    public @Nullable Entity entity() {
        return entity;
    }

    /**
     * Returns the tile hit.
     *
     * @return the tile cell, or {@code null} for an entity
     */
    public @Nullable GridPos tile() {
        if (!hasTile) {
            return null;
        }
        GridPos current = tile;
        if (current == null) {
            current = new GridPos(tileX, tileY);
            tile = current;
        }
        return current;
    }

    /**
     * Returns where the two touch.
     *
     * @return the point in world units
     */
    public Vec2 point() {
        Vec2 current = point;
        if (current == null) {
            current = new Vec2(pointX, pointY);
            point = current;
        }
        return current;
    }

    /**
     * Returns the unit normal pointing out of what was hit, towards the mover.
     *
     * @return the normal
     */
    public Vec2 normal() {
        Vec2 current = normal;
        if (current == null) {
            current = new Vec2(normalX, normalY);
            normal = current;
        }
        return current;
    }

    /**
     * Returns the x of {@link #point()} without creating a vector.
     *
     * @return world units
     */
    public float pointX() {
        return pointX;
    }

    /**
     * Returns the y of {@link #point()} without creating a vector.
     *
     * @return world units
     */
    public float pointY() {
        return pointY;
    }

    /**
     * Returns the x of {@link #normal()} without creating a vector.
     *
     * @return the component
     */
    public float normalX() {
        return normalX;
    }

    /**
     * Returns the y of {@link #normal()} without creating a vector.
     *
     * @return the component
     */
    public float normalY() {
        return normalY;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Contact c
                && entity == c.entity
                && hasTile == c.hasTile
                && tileX == c.tileX
                && tileY == c.tileY
                && pointX == c.pointX
                && pointY == c.pointY
                && normalX == c.normalX
                && normalY == c.normalY;
    }

    @Override
    public int hashCode() {
        return Objects.hash(entity, hasTile, tileX, tileY, pointX, pointY, normalX, normalY);
    }

    @Override
    public String toString() {
        return "Contact[" + (entity != null ? entity : tile()) + " at " + pointX + ", " + pointY + " normal " + normalX
                + ", " + normalY + "]";
    }
}
