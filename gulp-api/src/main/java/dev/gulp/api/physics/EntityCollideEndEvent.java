package dev.gulp.api.physics;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityEvent;
import dev.gulp.api.math.GridPos;
import org.jspecify.annotations.Nullable;

/**
 * An entity stopped touching another entity or a collision tile.
 *
 * <pre>{@code
 * player.on(EntityCollideEndEvent.class, e -> sliding = false);
 * }</pre>
 */
public final class EntityCollideEndEvent extends EntityEvent {

    private final @Nullable Entity other;
    private final @Nullable GridPos tile;

    /**
     * Creates the event.
     *
     * @param entity the entity told about it
     * @param other the other entity, or {@code null} for a tile
     * @param tile the tile, or {@code null}
     */
    public EntityCollideEndEvent(Entity entity, @Nullable Entity other, @Nullable GridPos tile) {
        super(entity);
        this.other = other;
        this.tile = tile;
    }

    /**
     * Returns the other entity.
     *
     * @return the entity, or {@code null} for a tile
     */
    public @Nullable Entity other() {
        return other;
    }

    /**
     * Returns the tile.
     *
     * @return the tile, or {@code null}
     */
    public @Nullable GridPos tile() {
        return tile;
    }
}
