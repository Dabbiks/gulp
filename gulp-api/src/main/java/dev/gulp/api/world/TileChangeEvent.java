package dev.gulp.api.world;

import dev.gulp.api.event.Cancellable;
import dev.gulp.api.event.Event;
import org.jspecify.annotations.Nullable;

/**
 * A tile is about to change. Cancelling it keeps the old tile.
 *
 * <pre>{@code
 * on(TileChangeEvent.class, e -> {
 *     if (e.from() == BEDROCK) { e.setCancelled(true); }
 * });
 * }</pre>
 */
public final class TileChangeEvent extends Event implements Cancellable {

    private final World world;
    private final TileLayer layer;
    private final int x;
    private final int y;
    private final @Nullable TileType from;
    private final @Nullable TileType to;

    /**
     * Creates the event.
     *
     * @param world the world
     * @param layer the layer
     * @param x the column
     * @param y the row
     * @param from the old tile, or {@code null}
     * @param to the new tile, or {@code null}
     */
    public TileChangeEvent(World world, TileLayer layer, int x, int y, @Nullable TileType from, @Nullable TileType to) {
        super();
        this.world = world;
        this.layer = layer;
        this.x = x;
        this.y = y;
        this.from = from;
        this.to = to;
    }

    /**
     * Returns the world.
     *
     * @return the world
     */
    public World world() {
        return world;
    }

    /**
     * Returns the layer.
     *
     * @return the layer
     */
    public TileLayer layer() {
        return layer;
    }

    /**
     * Returns the column.
     *
     * @return the column
     */
    public int x() {
        return x;
    }

    /**
     * Returns the row.
     *
     * @return the row
     */
    public int y() {
        return y;
    }

    /**
     * Returns the old tile, or {@code null}.
     *
     * @return the old tile, or {@code null}
     */
    public @Nullable TileType from() {
        return from;
    }

    /**
     * Returns the new tile, or {@code null}.
     *
     * @return the new tile, or {@code null}
     */
    public @Nullable TileType to() {
        return to;
    }

    private boolean cancelled;

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }
}
