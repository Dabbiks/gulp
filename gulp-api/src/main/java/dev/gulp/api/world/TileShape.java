package dev.gulp.api.world;

import dev.gulp.api.math.Polygon;
import dev.gulp.api.math.Vec2;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The collision shape of a tile, in tile coordinates ({@code 0..1}, y down). Used by physics and navigation (stage 7).
 *
 * <pre>{@code
 * TileType ramp = TileType.builder(key("ramp")).region(GameAssets.Sprites.RAMP).shape(TileShape.SLOPE_RIGHT).build();
 * TileShape notch = TileShape.polygon("notch", new Polygon(List.of(Vec2.ZERO, new Vec2(1, 0), new Vec2(0.5f, 1))));
 * }</pre>
 */
public final class TileShape {

    /** No collision. */
    public static final TileShape NONE = new TileShape("none", null);

    /** The whole tile. */
    public static final TileShape FULL = square("full", 0f, 0f, 1f, 1f);

    /** A slope rising to the left: the floor is high on the left edge. */
    public static final TileShape SLOPE_LEFT =
            new TileShape("slope_left", new Polygon(List.of(new Vec2(0, 0), new Vec2(1, 1), new Vec2(0, 1))));

    /** A slope rising to the right: the floor is high on the right edge. */
    public static final TileShape SLOPE_RIGHT =
            new TileShape("slope_right", new Polygon(List.of(new Vec2(1, 0), new Vec2(1, 1), new Vec2(0, 1))));

    /** The top half of the tile. */
    public static final TileShape HALF_TOP = square("half_top", 0f, 0f, 1f, 0.5f);

    /** A platform that blocks only from above. */
    public static final TileShape ONE_WAY = square("one_way", 0f, 0f, 1f, 0.1f);

    private final String name;
    private final @Nullable Polygon polygon;

    private TileShape(String name, @Nullable Polygon polygon) {
        this.name = name;
        this.polygon = polygon;
    }

    private static TileShape square(String name, float x, float y, float width, float height) {
        return new TileShape(
                name,
                new Polygon(List.of(
                        new Vec2(x, y),
                        new Vec2(x + width, y),
                        new Vec2(x + width, y + height),
                        new Vec2(x, y + height))));
    }

    /**
     * Returns a custom shape.
     *
     * @param name a name for debugging and saves
     * @param polygon points in tile coordinates
     * @return the shape
     */
    public static TileShape polygon(String name, Polygon polygon) {
        return new TileShape(name, polygon);
    }

    /**
     * Returns a built-in shape by name.
     *
     * @param name {@code none}, {@code full}, {@code slope_left}, {@code slope_right}, {@code half_top} or {@code
     *     one_way}
     * @return the shape, or {@code null} for other names
     */
    public static @Nullable TileShape byName(String name) {
        for (TileShape shape : List.of(NONE, FULL, SLOPE_LEFT, SLOPE_RIGHT, HALF_TOP, ONE_WAY)) {
            if (shape.name.equals(name)) {
                return shape;
            }
        }
        return null;
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the outline.
     *
     * @return points in tile coordinates, or {@code null} for {@link #NONE}
     */
    public @Nullable Polygon polygon() {
        return polygon;
    }

    /**
     * Returns whether the shape blocks anything.
     *
     * @return {@code false} only for {@link #NONE}
     */
    public boolean isSolid() {
        return polygon != null;
    }

    /**
     * Returns whether it blocks only from above.
     *
     * @return {@code true} for {@link #ONE_WAY}
     */
    public boolean isOneWay() {
        return this == ONE_WAY;
    }

    @Override
    public String toString() {
        return "TileShape[" + name + "]";
    }
}
