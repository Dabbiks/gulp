package dev.gulp.api.physics;

import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import java.util.List;

/**
 * A collision shape in world units, relative to the entity position (plus the collider offset). Y points down.
 * Movers and triggers accept boxes, circles, capsules and polygons; bodies and static colliders also accept segments
 * and chains. Concave polygons are split into convex parts automatically.
 *
 * <pre>{@code
 * entity.add(new Collider(Shape.capsule(0.35f, 1.6f)));
 * ground.add(new Body(BodyType.STATIC).shape(Shape.chain(List.of(a, b, c), false)));
 * }</pre>
 */
public sealed interface Shape
        permits Shape.Box, Shape.Circle, Shape.Capsule, Shape.Polygon, Shape.Segment, Shape.Chain {

    /**
     * Returns an axis-aligned box centred on the origin.
     *
     * @param width the width
     * @param height the height
     * @return the shape
     */
    static Box box(float width, float height) {
        return new Box(width, height);
    }

    /**
     * Returns a circle centred on the origin.
     *
     * @param radius the radius
     * @return the shape
     */
    static Circle circle(float radius) {
        return new Circle(radius);
    }

    /**
     * Returns an upright capsule centred on the origin.
     *
     * @param radius the radius of the rounded ends
     * @param height the total height, at least {@code 2 * radius}
     * @return the shape
     */
    static Capsule capsule(float radius, float height) {
        return new Capsule(radius, height);
    }

    /**
     * Returns a polygon; concave outlines are split into convex parts.
     *
     * @param points the outline, at least three points
     * @return the shape
     */
    static Polygon polygon(Vec2... points) {
        return new Polygon(List.of(points));
    }

    /**
     * Returns a polygon; concave outlines are split into convex parts.
     *
     * @param points the outline, at least three points
     * @return the shape
     */
    static Polygon polygon(List<Vec2> points) {
        return new Polygon(points);
    }

    /**
     * Returns a line segment, for static geometry.
     *
     * @param a one end
     * @param b the other end
     * @return the shape
     */
    static Segment segment(Vec2 a, Vec2 b) {
        return new Segment(a, b);
    }

    /**
     * Returns a chain of segments, for terrain outlines.
     *
     * @param points the points, at least two
     * @param loop whether the last point joins the first
     * @return the shape
     */
    static Chain chain(List<Vec2> points, boolean loop) {
        return new Chain(points, loop);
    }

    /**
     * Returns the bounds around the origin.
     *
     * @return the bounds
     */
    Rect bounds();

    /**
     * An axis-aligned box centred on the origin.
     *
     * @param width the width
     * @param height the height
     */
    record Box(float width, float height) implements Shape {
        /**
         * Validates the size.
         *
         * @param width the width
         * @param height the height
         */
        public Box {
            if (!(width > 0f) || !(height > 0f)) {
                throw new IllegalArgumentException("Box size must be positive: " + width + " x " + height);
            }
        }

        @Override
        public Rect bounds() {
            return new Rect(-width / 2f, -height / 2f, width, height);
        }
    }

    /**
     * A circle centred on the origin.
     *
     * @param radius the radius
     */
    record Circle(float radius) implements Shape {
        /**
         * Validates the radius.
         *
         * @param radius the radius
         */
        public Circle {
            if (!(radius > 0f)) {
                throw new IllegalArgumentException("Circle radius must be positive: " + radius);
            }
        }

        @Override
        public Rect bounds() {
            return new Rect(-radius, -radius, radius * 2f, radius * 2f);
        }
    }

    /**
     * An upright capsule centred on the origin.
     *
     * @param radius the radius of the ends
     * @param height the total height
     */
    record Capsule(float radius, float height) implements Shape {
        /**
         * Validates the size.
         *
         * @param radius the radius
         * @param height the height
         */
        public Capsule {
            if (!(radius > 0f) || height < radius * 2f) {
                throw new IllegalArgumentException("Capsule needs radius > 0 and height >= 2 * radius");
            }
        }

        @Override
        public Rect bounds() {
            return new Rect(-radius, -height / 2f, radius * 2f, height);
        }
    }

    /**
     * A simple polygon, convex or concave.
     *
     * @param points the outline
     */
    record Polygon(List<Vec2> points) implements Shape {
        /**
         * Copies and validates the outline.
         *
         * @param points the outline
         */
        public Polygon {
            points = List.copyOf(points);
            if (points.size() < 3) {
                throw new IllegalArgumentException("A polygon needs at least three points");
            }
        }

        @Override
        public Rect bounds() {
            return boundsOf(points);
        }
    }

    /**
     * A line segment.
     *
     * @param a one end
     * @param b the other end
     */
    record Segment(Vec2 a, Vec2 b) implements Shape {
        @Override
        public Rect bounds() {
            return boundsOf(List.of(a, b));
        }
    }

    /**
     * Connected segments.
     *
     * @param points the points
     * @param loop whether the last point joins the first
     */
    record Chain(List<Vec2> points, boolean loop) implements Shape {
        /**
         * Copies and validates the points.
         *
         * @param points the points
         * @param loop whether closed
         */
        public Chain {
            points = List.copyOf(points);
            if (points.size() < 2) {
                throw new IllegalArgumentException("A chain needs at least two points");
            }
        }

        @Override
        public Rect bounds() {
            return boundsOf(points);
        }
    }

    private static Rect boundsOf(List<Vec2> points) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (Vec2 p : points) {
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            maxX = Math.max(maxX, p.x());
            maxY = Math.max(maxY, p.y());
        }
        return new Rect(minX, minY, maxX - minX, maxY - minY);
    }
}
