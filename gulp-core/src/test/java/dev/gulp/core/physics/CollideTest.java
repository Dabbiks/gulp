package dev.gulp.core.physics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.gulp.api.math.Vec2;
import dev.gulp.api.physics.Shape;
import java.util.List;
import org.junit.jupiter.api.Test;

class CollideTest {

    private static Placed place(Shape shape, float x, float y) {
        return new Placed().set(Shapes.pieces(shape, 0f, 0f).get(0), x, y);
    }

    @Test
    void massProperties() {
        Convex box = Shapes.pieces(Shape.box(2f, 1f), 0f, 0f).get(0);
        assertThat(box.area).isCloseTo(2f, within(1e-5f));
        assertThat(box.centroidX).isCloseTo(0f, within(1e-5f));
        assertThat(box.inertia).as("m (w^2 + h^2) / 12").isCloseTo(2f * 5f / 12f, within(1e-4f));
        Convex triangle = Shapes.polygonPiece(List.of(Vec2.ZERO, new Vec2(3, 0), new Vec2(0, 3)), 0f, 0f);
        assertThat(triangle.area).isCloseTo(4.5f, within(1e-4f));
        assertThat(triangle.centroidX).isCloseTo(1f, within(1e-4f));
        assertThat(triangle.centroidY).isCloseTo(1f, within(1e-4f));
        Convex circle = Shapes.pieces(Shape.circle(1f), 0f, 0f).get(0);
        assertThat(circle.area).isCloseTo((float) Math.PI, within(1e-4f));
        Convex capsule = Shapes.pieces(Shape.capsule(0.5f, 3f), 1f, 2f).get(0);
        assertThat(capsule.count).isEqualTo(2);
        assertThat(capsule.centroidX).isEqualTo(1f);
        assertThat(capsule.area).isCloseTo(2f + (float) Math.PI * 0.25f, within(1e-4f));
        assertThat(Shapes.pieces(Shape.capsule(0.5f, 1f), 0f, 0f).get(0).count)
                .as("round capsule")
                .isEqualTo(1);
        assertThat(capsule.extent()).isCloseTo((float) Math.sqrt(1 + 9) + 0.5f, within(1e-4f));
        assertThatThrownBy(() -> new Convex(new float[9], new float[9], 9, 0f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void concavePolygonsSplitIntoConvexParts() {
        // An L shape, clockwise.
        List<Vec2> ell =
                List.of(new Vec2(0, 0), new Vec2(0, 3), new Vec2(3, 3), new Vec2(3, 2), new Vec2(1, 2), new Vec2(1, 0));
        List<List<Vec2>> parts = Shapes.convexParts(ell);
        assertThat(parts.size()).isBetween(2, 3);
        float area = 0f;
        for (List<Vec2> part : parts) {
            assertThat(Shapes.isConvex(part)).isTrue();
            area += Math.abs(Shapes.signedArea(part));
        }
        assertThat(area).isCloseTo(5f, within(1e-4f));
        // A convex polygon with more than eight points is fanned into pieces.
        List<Vec2> circle = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            circle.add(Vec2.fromAngle(i * 18f).scale(2f));
        }
        List<Convex> pieces = Shapes.pieces(Shape.polygon(circle), 0f, 0f);
        assertThat(pieces).allMatch(p -> p.count <= Convex.MAX_VERTICES);
        float total = 0f;
        for (Convex piece : pieces) {
            total += piece.area;
        }
        assertThat(total).isCloseTo(Math.abs(Shapes.signedArea(circle)), within(1e-3f));
        // A bow tie crosses itself.
        assertThatThrownBy(() -> Shapes.convexParts(
                        List.of(new Vec2(0, 0), new Vec2(2, 2), new Vec2(2, 0), new Vec2(0, 2), new Vec2(1, 3))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Shapes.convexParts(List.of(Vec2.ZERO, Vec2.ZERO, Vec2.ONE)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Shapes.pieces(Shape.chain(List.of(Vec2.ZERO, Vec2.ONE, Vec2.ONE, new Vec2(2, 0)), true), 0f, 0f))
                .as("closed chain without the zero-length link")
                .hasSize(3);
        assertThat(Shapes.pieces(Shape.segment(Vec2.ZERO, Vec2.ONE), 0f, 0f).get(0).count)
                .isEqualTo(2);
    }

    @Test
    void boxesTouchWithTwoPointsAndTheRightNormal() {
        Manifold m = new Manifold();
        Placed floor = place(Shape.box(10f, 1f), 0f, 0.5f);
        Placed box = place(Shape.box(1f, 1f), 0f, -0.49f);
        Collide.collide(box, floor, 0f, m);
        assertThat(m.count).isEqualTo(2);
        assertThat(m.ny).as("from the box down to the floor").isCloseTo(1f, within(1e-5f));
        assertThat(m.minSeparation()).isCloseTo(-0.01f, within(1e-4f));
        assertThat(Collide.overlaps(box, floor, m)).isTrue();
        Placed apart = place(Shape.box(1f, 1f), 0f, -2f);
        assertThat(Collide.collide(apart, floor, 0.1f, m).count).isZero();
        assertThat(Collide.collide(apart, floor, 2f, m).count).as("speculative").isEqualTo(2);
        // A rotated box resting on a corner has one point.
        Placed diamond = new Placed()
                .set(Shapes.pieces(Shape.box(1f, 1f), 0f, 0f).get(0), 0f, -0.7f, (float) Math.cos(Math.PI / 4), (float)
                        Math.sin(Math.PI / 4));
        Collide.collide(diamond, floor, 0f, m);
        assertThat(m.count).isEqualTo(1);
        // Distance between separated boxes.
        Collide.distance(apart, floor, m);
        assertThat(m.minSeparation()).isCloseTo(1.5f, within(1e-4f));
        Collide.distance(box, floor, m);
        assertThat(m.minSeparation()).isLessThan(0f);
    }

    @Test
    void circlesCapsulesAndSegments() {
        Manifold m = new Manifold();
        Placed a = place(Shape.circle(1f), 0f, 0f);
        Placed b = place(Shape.circle(1f), 1.5f, 0f);
        Collide.collide(a, b, 0f, m);
        assertThat(m.count).isEqualTo(1);
        assertThat(m.nx).isEqualTo(1f);
        assertThat(m.separation[0]).isCloseTo(-0.5f, within(1e-5f));
        Placed same = place(Shape.circle(1f), 0f, 0f);
        assertThat(Collide.collide(a, same, 0f, m).ny)
                .as("coincident centres pick a normal")
                .isEqualTo(-1f);
        Placed box = place(Shape.box(2f, 2f), 0f, 0f);
        Placed inside = place(Shape.circle(0.2f), 0.5f, 0f);
        Collide.collide(box, inside, 0f, m);
        assertThat(m.count).isEqualTo(1);
        assertThat(m.nx).isEqualTo(1f);
        Collide.collide(inside, box, 0f, m);
        assertThat(m.nx).as("flipped for circle first").isEqualTo(-1f);
        Placed corner = place(Shape.circle(0.5f), 1.3f, 1.3f);
        Collide.collide(box, corner, 0f, m);
        assertThat(m.count).isEqualTo(1);
        assertThat(m.nx).isCloseTo((float) Math.sqrt(0.5), within(1e-4f));
        // A capsule standing on a box, and two crossing capsules.
        Placed capsule = place(Shape.capsule(0.5f, 2f), 0f, -1.99f);
        Collide.collide(capsule, box, 0f, m);
        assertThat(m.count).isGreaterThan(0);
        assertThat(m.ny).isCloseTo(1f, within(1e-4f));
        Placed lying =
                new Placed().set(Shapes.pieces(Shape.capsule(0.5f, 2f), 0f, 0f).get(0), 0f, -1.49f, 0f, 1f);
        Collide.collide(lying, box, 0f, m);
        assertThat(m.count).as("a capsule lying flat touches along its side").isEqualTo(2);
        Placed tilted = new Placed()
                .set(
                        Shapes.pieces(Shape.capsule(0.2f, 1f), 0f, 0f).get(0),
                        1.5f,
                        -1.5f,
                        (float) Math.cos(Math.PI / 4),
                        (float) Math.sin(Math.PI / 4));
        Collide.collide(tilted, box, 0.5f, m);
        assertThat(m.count).isEqualTo(1);
        Placed segment = place(Shape.segment(new Vec2(-2, 0), new Vec2(2, 0)), 0f, 3f);
        Placed ball = place(Shape.circle(0.5f), 0f, 2.6f);
        Collide.collide(segment, ball, 0f, m);
        assertThat(m.count).isEqualTo(1);
        assertThat(m.ny).isEqualTo(-1f);
        Collide.distance(segment, ball, m);
        assertThat(m.minSeparation()).isCloseTo(-0.1f, within(1e-4f));
    }

    @Test
    void raysAndPoints() {
        Placed box = place(Shape.box(2f, 2f), 5f, 0f);
        assertThat(Collide.raycast(box, 0f, 0f, 10f, 0f, 1f)).isTrue();
        assertThat(Collide.rayFraction).isCloseTo(0.4f, within(1e-5f));
        assertThat(Collide.rayNormalX).isEqualTo(-1f);
        assertThat(Collide.raycast(box, 0f, 0f, 10f, 0f, 0.3f)).isFalse();
        assertThat(Collide.raycast(box, 0f, 5f, 10f, 0f, 1f)).isFalse();
        assertThat(Collide.raycast(box, 5f, 0f, 10f, 0f, 1f))
                .as("starting inside")
                .isFalse();
        Placed circle = place(Shape.circle(1f), 5f, 0f);
        assertThat(Collide.raycast(circle, 0f, 0f, 10f, 0f, 1f)).isTrue();
        assertThat(Collide.rayFraction).isCloseTo(0.4f, within(1e-5f));
        assertThat(Collide.raycast(circle, 0f, 3f, 10f, 0f, 1f)).isFalse();
        assertThat(Collide.raycast(circle, 5f, 0f, 10f, 0f, 1f)).isFalse();
        Placed capsule = place(Shape.capsule(0.5f, 3f), 5f, 0f);
        assertThat(Collide.raycast(capsule, 0f, 0f, 10f, 0f, 1f)).as("the side").isTrue();
        assertThat(Collide.rayFraction).isCloseTo(0.45f, within(1e-5f));
        assertThat(Collide.raycast(capsule, 5f, -5f, 0f, 10f, 1f))
                .as("the round end")
                .isTrue();
        assertThat(Collide.rayFraction).isCloseTo(0.35f, within(1e-5f));
        Placed segment = place(Shape.segment(new Vec2(0, -1), new Vec2(0, 1)), 5f, 0f);
        assertThat(Collide.raycast(segment, 0f, 0f, 10f, 0f, 1f)).isTrue();
        assertThat(Collide.rayNormalX).isEqualTo(-1f);
        assertThat(Collide.raycast(segment, 10f, 0f, -10f, 0f, 1f))
                .as("from the other side")
                .isTrue();
        assertThat(Collide.rayNormalX).isEqualTo(1f);
        assertThat(Collide.raycast(segment, 0f, 3f, 10f, 0f, 1f)).isFalse();
        assertThat(Collide.contains(box, 5.5f, 0.5f)).isTrue();
        assertThat(Collide.contains(box, 7f, 0f)).isFalse();
        assertThat(Collide.contains(circle, 5.5f, 0.5f)).isTrue();
        assertThat(Collide.contains(capsule, 5f, 1.4f)).isTrue();
        assertThat(Collide.contains(capsule, 5.6f, 0f)).isFalse();
        Placed copy = new Placed().copy(box);
        copy.move(1f, 0f);
        assertThat(copy.minX).isEqualTo(box.minX + 1f);
        assertThat(copy.overlapsBox(0f, 0f, 1f, 1f)).isFalse();
    }

    @Test
    void keysOfTiles() {
        long key = PhysicsWorld.tileKey(-5, 123);
        assertThat(PhysicsWorld.isTileKey(key)).isTrue();
        assertThat(PhysicsWorld.tileX(key)).isEqualTo(-5);
        assertThat(PhysicsWorld.tileY(key)).isEqualTo(123);
        assertThat(PhysicsWorld.isTileKey(42L)).isFalse();
    }
}
