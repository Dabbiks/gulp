package dev.gulp.api.particle;

/**
 * Where an emitter creates particles, around its position.
 *
 * <pre>{@code
 * EmitterConfig.builder().shape(EmitterShape.ring(1.5f, 0.2f)).build();
 * }</pre>
 */
public sealed interface EmitterShape
        permits EmitterShape.Point, EmitterShape.Circle, EmitterShape.Ring, EmitterShape.Rect, EmitterShape.Line {

    /**
     * Returns a single point.
     *
     * @return the shape
     */
    static Point point() {
        return new Point();
    }

    /**
     * Returns a filled circle.
     *
     * @param radius world units
     * @return the shape
     */
    static Circle circle(float radius) {
        return new Circle(radius);
    }

    /**
     * Returns a ring.
     *
     * @param radius the middle radius, world units
     * @param thickness how wide the ring is
     * @return the shape
     */
    static Ring ring(float radius, float thickness) {
        return new Ring(radius, thickness);
    }

    /**
     * Returns a filled rectangle centred on the emitter.
     *
     * @param width world units
     * @param height world units
     * @return the shape
     */
    static Rect rect(float width, float height) {
        return new Rect(width, height);
    }

    /**
     * Returns a horizontal line centred on the emitter.
     *
     * @param length world units
     * @return the shape
     */
    static Line line(float length) {
        return new Line(length);
    }

    /** A single point. */
    record Point() implements EmitterShape {}

    /**
     * A filled circle.
     *
     * @param radius world units
     */
    record Circle(float radius) implements EmitterShape {}

    /**
     * A ring.
     *
     * @param radius the middle radius
     * @param thickness the width
     */
    record Ring(float radius, float thickness) implements EmitterShape {}

    /**
     * A filled rectangle.
     *
     * @param width world units
     * @param height world units
     */
    record Rect(float width, float height) implements EmitterShape {}

    /**
     * A horizontal line.
     *
     * @param length world units
     */
    record Line(float length) implements EmitterShape {}
}
