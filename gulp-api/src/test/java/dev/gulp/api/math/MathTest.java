package dev.gulp.api.math;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.gulp.api.graphics.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Property tests: random inputs from a fixed seed, invariants that must always hold. */
class MathTest {

    private static final int RUNS = 500;
    private final Rng rng = new Rng(1234);

    private Vec2 randomVec() {
        return new Vec2(rng.nextFloat(-100, 100), rng.nextFloat(-100, 100));
    }

    @Test
    void vectorProperties() {
        for (int i = 0; i < RUNS; i++) {
            Vec2 a = randomVec();
            Vec2 b = randomVec();
            float angle = rng.nextFloat(-720, 720);
            if (a.length() > 1e-3f) {
                assertThat(a.normalized().length()).isCloseTo(1f, within(1e-5f));
            }
            assertThat(a.add(b).sub(b).nearlyEquals(a)).isTrue();
            assertThat(a.dot(b)).isCloseTo(b.dot(a), within(1e-2f));
            assertThat(a.cross(b)).isCloseTo(-b.cross(a), within(1e-2f));
            assertThat(a.rotated(angle).length()).isCloseTo(a.length(), within(1e-2f));
            assertThat(a.rotated(angle).rotated(-angle).distanceTo(a)).isLessThan(1e-2f);
            assertThat(a.perpendicular().dot(a)).isCloseTo(0f, within(1e-2f));
            assertThat(a.lerp(b, 0f)).isEqualTo(a);
            assertThat(a.lerp(b, 1f).nearlyEquals(b)).isTrue();
            assertThat(a.moveToward(b, 1f).distanceTo(a)).isLessThanOrEqualTo(1.0001f);
            assertThat(a.clampLength(5f).length()).isLessThanOrEqualTo(5.0001f);
            assertThat(a.distanceTo(b)).isCloseTo(b.distanceTo(a), within(1e-3f));
            assertThat(a.distanceSquaredTo(b)).isCloseTo(a.distanceTo(b) * a.distanceTo(b), within(1f));
            MutableVec2 m = a.toMutable().rotate(angle);
            assertThat(m.toVec2().distanceTo(a.rotated(angle))).isLessThan(1e-3f);
        }
        assertThat(Vec2.UP).isEqualTo(new Vec2(0, -1));
        assertThat(Vec2.fromAngle(90).nearlyEquals(Vec2.DOWN)).isTrue();
        assertThat(Vec2.RIGHT.angle()).isZero();
        assertThat(Vec2.RIGHT.angleTo(Vec2.DOWN)).isCloseTo(90f, within(1e-4f));
        assertThat(Vec2.ZERO.normalized()).isEqualTo(Vec2.ZERO);
        assertThat(Vec2.ZERO.directionTo(Vec2.ZERO)).isEqualTo(Vec2.ZERO);
        assertThat(new Vec2(1, 2).reflect(new Vec2(0, -1))).isEqualTo(new Vec2(1, -2));
        assertThat(new Vec2(1.26f, 3.74f).snapped(0.5f)).isEqualTo(new Vec2(1.5f, 3.5f));
        assertThat(Vec2.of(1, 2).withX(3).withY(4).scale(2, 0.5f)).isEqualTo(new Vec2(6, 2));
        assertThat(Vec2.ONE.add(1, 1).toString()).isEqualTo("(2.0, 2.0)");
        assertThat(Vec2.ZERO.moveToward(Vec2.RIGHT, 5)).isEqualTo(Vec2.RIGHT);
        assertThat(Vec2.LEFT.directionTo(Vec2.RIGHT)).isEqualTo(Vec2.RIGHT);
        assertThat(Vec2.ONE.clampLength(10)).isSameAs(Vec2.ONE);
    }

    @Test
    void mutableVectorDoesNotAllocateAndMatches() {
        MutableVec2 v = new MutableVec2();
        assertThat(v.set(3, 4).length()).isEqualTo(5f);
        assertThat(v.lengthSquared()).isEqualTo(25f);
        assertThat(v.dot(1, 0)).isEqualTo(3f);
        v.normalize();
        assertThat(v.length()).isCloseTo(1f, within(1e-6f));
        v.set(Vec2.ZERO).normalize();
        assertThat(v.x).isZero();
        v.set(new MutableVec2(1, 1)).add(1, 1).add(Vec2.ONE).sub(1, 1).scale(2);
        assertThat(v.toVec2()).isEqualTo(new Vec2(4, 4));
        v.lerp(0, 0, 0.5f);
        assertThat(v.toVec2()).isEqualTo(new Vec2(2, 2));
        v.set(0, 0).moveToward(10, 0, 3);
        assertThat(v.x).isEqualTo(3f);
        v.moveToward(4, 0, 3);
        assertThat(v.x).isEqualTo(4f);
        v.set(10, 0).clampLength(2);
        assertThat(v.x).isEqualTo(2f);
        assertThat(v.toString()).isEqualTo("(2.0, 0.0)");
    }

    @Test
    void mathfFunctions() {
        assertThat(Mathf.clamp(5f, 0f, 1f)).isEqualTo(1f);
        assertThat(Mathf.clamp(-5, 0, 3)).isZero();
        assertThat(Mathf.lerp(0, 10, 0.3f)).isCloseTo(3f, within(1e-6f));
        assertThat(Mathf.inverseLerp(0, 10, 3)).isCloseTo(0.3f, within(1e-6f));
        assertThat(Mathf.inverseLerp(5, 5, 3)).isZero();
        assertThat(Mathf.remap(5, 0, 10, 100, 200)).isEqualTo(150f);
        assertThat(Mathf.smoothStep(0, 1, 0.5f)).isEqualTo(0.5f);
        assertThat(Mathf.smoothStep(0, 1, 2f)).isEqualTo(1f);
        assertThat(Mathf.moveToward(0, 10, 3)).isEqualTo(3f);
        assertThat(Mathf.moveToward(9, 10, 3)).isEqualTo(10f);
        assertThat(Mathf.approach(0, 10, 0.5f)).isEqualTo(5f);
        assertThat(Mathf.wrap(370f, 0f, 360f)).isCloseTo(10f, within(1e-4f));
        assertThat(Mathf.wrap(-10f, 0f, 360f)).isCloseTo(350f, within(1e-4f));
        assertThat(Mathf.wrap(5f, 1f, 1f)).isEqualTo(1f);
        assertThat(Mathf.wrap(-1, 0, 4)).isEqualTo(3);
        assertThat(Mathf.wrap(7, 3, 3)).isEqualTo(3);
        assertThat(Mathf.pingPong(3f, 2f)).isCloseTo(1f, within(1e-5f));
        assertThat(Mathf.snapped(7f, 5f)).isEqualTo(5f);
        assertThat(Mathf.snapped(7f, 0f)).isEqualTo(7f);
        assertThat(Mathf.sign(-3f)).isEqualTo(-1f);
        assertThat(Mathf.isZero(1e-7f)).isTrue();
        assertThat(Mathf.nearlyEqual(1000000f, 1000000.5f)).isTrue();
        assertThat(Mathf.nearlyEqual(1f, 1.1f)).isFalse();
        assertThat(Mathf.angleDifference(350, 10)).isCloseTo(20f, within(1e-4f));
        assertThat(Mathf.angleDifference(0, 180)).isEqualTo(180f);
        assertThat(Mathf.lerpAngle(350, 10, 0.5f)).isCloseTo(360f, within(1e-4f));
        assertThat(Mathf.radToDeg(Mathf.degToRad(57f))).isCloseTo(57f, within(1e-4f));
        float x = 0f;
        for (int i = 0; i < 60; i++) {
            x = Mathf.damp(x, 10f, 5f, 1f / 60f);
        }
        float y = 0f;
        for (int i = 0; i < 30; i++) {
            y = Mathf.damp(y, 10f, 5f, 1f / 30f);
        }
        assertThat(x).as("frame-rate independent").isCloseTo(y, within(1e-3f));
    }

    @Test
    void rectsAndShapes() {
        Rect a = Rect.of(0, 0, 10, 10);
        Rect b = Rect.of(5, 5, 10, 10);
        assertThat(a.overlaps(b)).isTrue();
        assertThat(a.intersection(b)).isEqualTo(Rect.of(5, 5, 5, 5));
        assertThat(a.intersection(Rect.of(20, 20, 1, 1))).isEqualTo(Rect.EMPTY);
        assertThat(a.union(b)).isEqualTo(Rect.of(0, 0, 15, 15));
        assertThat(a.contains(10, 5)).isFalse();
        assertThat(a.contains(new Vec2(9.9f, 0))).isTrue();
        assertThat(a.contains(Rect.of(1, 1, 2, 2))).isTrue();
        assertThat(a.center()).isEqualTo(new Vec2(5, 5));
        assertThat(a.area()).isEqualTo(100f);
        assertThat(a.expand(1)).isEqualTo(Rect.of(-1, -1, 12, 12));
        assertThat(a.expand(-10).width()).isZero();
        assertThat(a.translate(1, 2)).isEqualTo(Rect.of(1, 2, 10, 10));
        assertThat(Rect.of(0, 0, 4, 3).fitInside(Rect.of(0, 0, 100, 50)))
                .isEqualTo(Rect.of(100f / 3f * 0 + (100 - 200f / 3f) / 2f, 0, 200f / 3f, 50));
        assertThat(Rect.of(0, 0, 4, 3).fitOutside(Rect.of(0, 0, 100, 50)).width())
                .isEqualTo(100f);
        assertThat(Rect.of(0, 0, 0, 0).fitInside(a).width()).isZero();
        assertThat(Rect.of(0, 0, 0, 0).fitOutside(a).width()).isZero();
        assertThat(Rect.fromCorners(new Vec2(5, 5), new Vec2(1, 2))).isEqualTo(Rect.of(1, 2, 4, 3));
        assertThat(a.clamp(new Vec2(-5, 20))).isEqualTo(new Vec2(0, 10));
        assertThatThrownBy(() -> Rect.of(0, 0, -1, 1)).isInstanceOf(IllegalArgumentException.class);

        Circle c = new Circle(Vec2.ZERO, 2);
        assertThat(c.contains(new Vec2(1, 1))).isTrue();
        assertThat(c.overlaps(new Circle(new Vec2(3, 0), 1.5f))).isTrue();
        assertThat(c.area()).isCloseTo(4 * Mathf.PI, within(1e-4f));
        assertThat(c.bounds()).isEqualTo(Rect.of(-2, -2, 4, 4));
        assertThatThrownBy(() -> new Circle(Vec2.ZERO, -1)).isInstanceOf(IllegalArgumentException.class);

        Segment s = new Segment(Vec2.ZERO, new Vec2(10, 0));
        assertThat(s.length()).isEqualTo(10f);
        assertThat(s.pointAt(0.5f)).isEqualTo(new Vec2(5, 0));
        assertThat(s.closestPoint(new Vec2(20, 5))).isEqualTo(new Vec2(10, 0));
        assertThat(s.distanceTo(new Vec2(5, 3))).isEqualTo(3f);
        assertThat(new Segment(Vec2.ONE, Vec2.ONE).closestPoint(Vec2.ZERO)).isEqualTo(Vec2.ONE);

        Capsule capsule = new Capsule(s, 1);
        assertThat(capsule.contains(new Vec2(5, 0.9f))).isTrue();
        assertThat(capsule.contains(new Vec2(12, 0))).isFalse();
        assertThat(capsule.bounds()).isEqualTo(Rect.of(-1, -1, 12, 2));
        assertThatThrownBy(() -> new Capsule(s, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void polygons() {
        Polygon square = Polygon.of(0, 0, 2, 0, 2, 2, 0, 2);
        assertThat(square.area()).isEqualTo(4f);
        assertThat(square.signedArea()).isEqualTo(4f);
        assertThat(square.centroid()).isEqualTo(new Vec2(1, 1));
        assertThat(square.contains(new Vec2(1, 1))).isTrue();
        assertThat(square.contains(new Vec2(3, 1))).isFalse();
        assertThat(square.isConvex()).isTrue();
        assertThat(square.bounds()).isEqualTo(Rect.of(0, 0, 2, 2));
        Polygon moved = square.transformed(Transform2D.of(new Vec2(10, 0), 0, new Vec2(2, 2)));
        assertThat(moved.bounds()).isEqualTo(Rect.of(10, 0, 4, 4));
        Polygon lShape = Polygon.of(0, 0, 3, 0, 3, 1, 1, 1, 1, 3, 0, 3);
        assertThat(lShape.isConvex()).isFalse();
        assertThat(Polygon.of(0, 0, 1, 0, 2, 0).centroid()).isEqualTo(new Vec2(1, 0));
        assertThatThrownBy(() -> Polygon.of(0, 0, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Polygon.of(0, 0, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transformsAndMatrices() {
        for (int i = 0; i < RUNS; i++) {
            Vec2 p = randomVec();
            Vec2 translation = randomVec();
            float angle = rng.nextFloat(-180, 180);
            Vec2 scale = new Vec2(rng.nextFloat(0.5f, 3f), rng.nextFloat(0.5f, 3f));
            Transform2D t = Transform2D.of(translation, angle, scale);
            Mat3 m = t.toMat3();
            Vec2 viaMatrix = m.apply(p);
            Vec2 viaAffine = t.apply(p);
            assertThat(viaMatrix.distanceTo(viaAffine)).isLessThan(1e-2f);
            Vec2 manual = p.scale(scale.x(), scale.y()).rotated(angle).add(translation);
            assertThat(viaAffine.distanceTo(manual)).isLessThan(1e-2f);
            assertThat(m.inverse().apply(viaMatrix).distanceTo(p)).isLessThan(1e-2f);
            Affine2 inverse = t.toAffine().invert();
            assertThat(inverse.apply(viaAffine).distanceTo(p)).isLessThan(1e-2f);
        }
        Affine2 a = new Affine2().setTo(5, 5, 90, 2, 2);
        Affine2 b = new Affine2().translate(1, 0);
        Vec2 composed = new Affine2().set(a).mul(b).apply(Vec2.ZERO);
        assertThat(composed.distanceTo(a.apply(b.apply(Vec2.ZERO)))).isLessThan(1e-4f);
        assertThat(a.averageScale()).isCloseTo(2f, within(1e-4f));
        assertThat(a.isAxisAligned()).isFalse();
        assertThat(new Affine2().scale(2, 3).isAxisAligned()).isTrue();
        assertThat(new Affine2().rotate(0).identity().determinant()).isEqualTo(1f);
        assertThat(new Affine2().shear(1, 0).transformX(0, 1)).isEqualTo(1f);
        assertThat(new Affine2().toString()).contains("1.0");
        assertThatThrownBy(() -> new Affine2().scale(0, 1).invert()).isInstanceOf(IllegalStateException.class);
        assertThat(Transform2D.IDENTITY
                        .withPosition(Vec2.ONE)
                        .withRotation(0)
                        .withScale(Vec2.ONE)
                        .apply(Vec2.ZERO))
                .isEqualTo(Vec2.ONE);
        Transform2D skewed = new Transform2D(Vec2.ZERO, 0, Vec2.ONE, new Vec2(45, 0));
        assertThat(skewed.apply(new Vec2(0, 1)).x()).isCloseTo(1f, within(1e-5f));

        Mat3 ortho = Mat3.orthographic(0, 640, 0, 360);
        assertThat(ortho.apply(new Vec2(0, 0)).nearlyEquals(new Vec2(-1, 1))).isTrue();
        assertThat(ortho.apply(new Vec2(640, 360)).nearlyEquals(new Vec2(1, -1)))
                .isTrue();
        assertThat(Mat3.IDENTITY.mul(Mat3.translation(1, 2)).apply(Vec2.ZERO)).isEqualTo(new Vec2(1, 2));
        assertThat(Mat3.rotation(90).mul(Mat3.scaling(2, 2)).determinant()).isCloseTo(4f, within(1e-4f));
        float[] columns = Mat3.translation(3, 4).toColumnMajor(new float[9]);
        assertThat(columns[6]).isEqualTo(3f);
        assertThat(columns[7]).isEqualTo(4f);
        assertThatThrownBy(() -> Mat3.scaling(0, 1).inverse()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void easingCurvesStartAndEndAtTheirEnds() {
        for (Ease ease : Ease.values()) {
            assertThat(ease.apply(0f)).as(ease.name()).isZero();
            assertThat(ease.apply(1f)).as(ease.name()).isEqualTo(1f);
            assertThat(ease.apply(-1f)).isZero();
            assertThat(ease.apply(2f)).isEqualTo(1f);
            float mid = ease.apply(0.5f);
            assertThat(mid).as(ease.name()).isBetween(-0.5f, 1.5f);
        }
        assertThat(Ease.IN_OUT_QUAD.apply(0.5f)).isCloseTo(0.5f, within(1e-6f));
        assertThat(Ease.IN_QUAD.apply(0.5f)).isEqualTo(0.25f);
        assertThat(Ease.OUT_QUAD.apply(0.5f)).isEqualTo(0.75f);
        assertThat(Ease.OUT_IN_CUBIC.apply(0.5f)).isCloseTo(0.5f, within(1e-6f));
        assertThat(Ease.IN_BACK.apply(0.2f)).as("back overshoots below zero").isNegative();
        assertThat(Ease.LINEAR.apply(10, 20, 0.5f)).isEqualTo(15f);
        Interpolation custom = Ease.custom(t -> t * t);
        assertThat(custom.apply(0.5f)).isEqualTo(0.25f);
        Interpolation linearBezier = Ease.cubicBezier(0.25f, 0.25f, 0.75f, 0.75f);
        for (float t = 0.05f; t < 1f; t += 0.1f) {
            assertThat(linearBezier.apply(t)).isCloseTo(t, within(1e-3f));
        }
        assertThat(linearBezier.apply(0)).isZero();
        assertThat(linearBezier.apply(1)).isEqualTo(1f);
        assertThatThrownBy(() -> Ease.cubicBezier(2, 0, 0, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void curvesAreSampledByArcLength() {
        Curve line = Bezier.quadratic(Vec2.ZERO, new Vec2(5, 0), new Vec2(10, 0));
        assertThat(line.length()).isCloseTo(10f, within(1e-3f));
        assertThat(line.atDistance(2.5f).x()).isCloseTo(2.5f, within(1e-2f));
        assertThat(line.atFraction(1f).x()).isCloseTo(10f, within(1e-3f));
        assertThat(line.parameterAtDistance(-5)).isZero();
        Curve cubic = Bezier.cubic(Vec2.ZERO, new Vec2(0, 10), new Vec2(10, 10), new Vec2(10, 0));
        assertThat(cubic.at(0)).isEqualTo(Vec2.ZERO);
        assertThat(cubic.at(1).nearlyEquals(new Vec2(10, 0))).isTrue();
        List<Vec2> points = List.of(Vec2.ZERO, new Vec2(10, 0), new Vec2(10, 10), new Vec2(0, 10));
        Curve spline = CatmullRom.through(points);
        for (int i = 0; i < points.size(); i++) {
            assertThat(spline.at(i / 3f).distanceTo(points.get(i))).isLessThan(1e-3f);
        }
        Curve bspline = BSpline.of(points);
        assertThat(bspline.at(0).distanceTo(points.getFirst())).isLessThan(1e-3f);
        assertThat(bspline.at(1).distanceTo(points.getLast())).isLessThan(1e-3f);
        float speedCheck = spline.atDistance(1f).distanceTo(spline.atDistance(2f));
        assertThat(speedCheck).isCloseTo(1f, within(0.05f));
        assertThatThrownBy(() -> CatmullRom.through(List.of(Vec2.ZERO))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BSpline.of(List.of(Vec2.ZERO))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void intersections() {
        Segment a = new Segment(Vec2.ZERO, new Vec2(10, 10));
        Segment b = new Segment(new Vec2(0, 10), new Vec2(10, 0));
        assertThat(Intersect.segments(a, b)).isEqualTo(new Vec2(5, 5));
        assertThat(Intersect.segments(a, new Segment(new Vec2(1, 0), new Vec2(11, 10))))
                .isNull();
        assertThat(Intersect.segments(a, new Segment(new Vec2(20, 0), new Vec2(30, 10))))
                .isNull();
        Rect rect = Rect.of(0, 0, 4, 4);
        assertThat(Intersect.pointRect(new Vec2(1, 1), rect)).isTrue();
        assertThat(Intersect.rects(rect, Rect.of(3, 3, 1, 1))).isTrue();
        assertThat(Intersect.circleRect(new Circle(new Vec2(5, 2), 1.5f), rect)).isTrue();
        assertThat(Intersect.circleRect(new Circle(new Vec2(7, 2), 1.5f), rect)).isFalse();
        assertThat(Intersect.circles(new Circle(Vec2.ZERO, 1), new Circle(new Vec2(1.5f, 0), 1)))
                .isTrue();
        assertThat(Intersect.pointCircle(Vec2.ZERO, new Circle(Vec2.ONE, 2))).isTrue();
        assertThat(Intersect.segmentCircle(new Segment(new Vec2(-5, 0), new Vec2(5, 0)), new Circle(Vec2.ZERO, 1)))
                .isTrue();
        assertThat(Intersect.segmentRect(new Segment(new Vec2(-1, 2), new Vec2(10, 2)), rect))
                .isTrue();
        assertThat(Intersect.segmentRect(new Segment(new Vec2(1, 1), new Vec2(2, 2)), rect))
                .isTrue();
        assertThat(Intersect.segmentRect(new Segment(new Vec2(-1, -1), new Vec2(-2, 10)), rect))
                .isFalse();
        Polygon square = Polygon.of(0, 0, 2, 0, 2, 2, 0, 2);
        Polygon shifted = Polygon.of(1.5f, 0, 3.5f, 0, 3.5f, 2, 1.5f, 2);
        Polygon far = Polygon.of(10, 10, 11, 10, 11, 11);
        assertThat(Intersect.pointPolygon(Vec2.ONE, square)).isTrue();
        assertThat(Intersect.polygons(square, shifted)).isTrue();
        assertThat(Intersect.polygons(square, far)).isFalse();
        assertThat(Intersect.polygons(square, Polygon.of(0.5f, 0.5f, 1, 0.5f, 1, 1)))
                .isTrue();
        assertThat(Intersect.circlePolygon(new Circle(new Vec2(3, 1), 1.1f), square))
                .isTrue();
        assertThat(Intersect.circlePolygon(new Circle(Vec2.ONE, 0.1f), square)).isTrue();
        assertThat(Intersect.circlePolygon(new Circle(new Vec2(5, 5), 1), square))
                .isFalse();
        Vec2 push = Intersect.sat(shifted, square);
        assertThat(push).isNotNull();
        assertThat(push.x()).isCloseTo(0.5f, within(1e-4f));
        assertThat(Intersect.sat(square, far)).isNull();
    }

    @Test
    void geometryAlgorithms() {
        Polygon lShape = Polygon.of(0, 0, 3, 0, 3, 1, 1, 1, 1, 3, 0, 3);
        int[] triangles = Geometry.triangulate(lShape);
        assertThat(triangles).hasSize(12);
        float area = 0f;
        for (int i = 0; i < triangles.length; i += 3) {
            area += new Polygon(List.of(
                            lShape.vertices().get(triangles[i]),
                            lShape.vertices().get(triangles[i + 1]),
                            lShape.vertices().get(triangles[i + 2])))
                    .area();
        }
        assertThat(area).isCloseTo(lShape.area(), within(1e-4f));
        List<Polygon> parts = Geometry.decompose(lShape);
        assertThat(parts).allMatch(Polygon::isConvex);
        assertThat(parts.stream().map(Polygon::area).reduce(0f, Float::sum)).isCloseTo(5f, within(1e-4f));
        assertThat(parts.size()).isLessThan(4);
        assertThat(Geometry.decompose(Polygon.of(0, 0, 1, 0, 1, 1))).hasSize(1);
        Polygon reversed = Polygon.of(0, 3, 1, 3, 1, 1, 3, 1, 3, 0, 0, 0);
        assertThat(Geometry.triangulate(reversed)).hasSize(12);

        List<Vec2> cloud = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            cloud.add(rng.insideCircle(10));
        }
        cloud.add(new Vec2(20, 0));
        List<Vec2> hull = Geometry.convexHull(cloud);
        Polygon hullPolygon = new Polygon(hull);
        assertThat(hullPolygon.isConvex()).isTrue();
        assertThat(hull).contains(new Vec2(20, 0));
        for (Vec2 p : cloud) {
            assertThat(hullPolygon.contains(p) || hull.contains(p) || onEdge(hull, p))
                    .isTrue();
        }
        assertThat(Geometry.convexHull(List.of(Vec2.ZERO, Vec2.ONE))).hasSize(2);

        List<Vec2> line = List.of(Vec2.ZERO, new Vec2(1, 0.01f), new Vec2(2, 0), new Vec2(3, 5));
        assertThat(Geometry.simplify(line, 0.1f)).containsExactly(Vec2.ZERO, new Vec2(2, 0), new Vec2(3, 5));
        assertThat(Geometry.simplify(List.of(Vec2.ZERO, Vec2.ONE), 1)).hasSize(2);
    }

    private static boolean onEdge(List<Vec2> hull, Vec2 p) {
        for (int i = 0; i < hull.size(); i++) {
            if (new Segment(hull.get(i), hull.get((i + 1) % hull.size())).distanceTo(p) < 1e-3f) {
                return true;
            }
        }
        return false;
    }

    @Test
    void rngIsDeterministicAndInRange() {
        Rng a = new Rng(42);
        Rng b = new Rng(42);
        for (int i = 0; i < RUNS; i++) {
            assertThat(a.nextLong()).isEqualTo(b.nextLong());
            assertThat(a.nextInt(3, 7)).isBetween(3, 7);
            assertThat(a.nextInt(5)).isBetween(0, 4);
            assertThat(a.nextFloat()).isBetween(0f, 1f);
            assertThat(a.nextFloat(-2, 2)).isBetween(-2f, 2f);
            assertThat(a.nextDouble()).isBetween(0.0, 1.0);
            assertThat(a.insideCircle(3).length()).isLessThanOrEqualTo(3.0001f);
            assertThat(a.onCircle(3).length()).isCloseTo(3f, within(1e-4f));
            b.nextInt(3, 7);
            b.nextInt(5);
            b.nextFloat();
            b.nextFloat(-2, 2);
            b.nextDouble();
            b.insideCircle(3);
            b.onCircle(3);
        }
        int trues = 0;
        for (int i = 0; i < 10000; i++) {
            trues += a.chance(0.25f) ? 1 : 0;
            a.nextBoolean();
            a.nextInt();
        }
        assertThat(trues).isBetween(2200, 2800);
        Map<String, Integer> counts = new HashMap<>();
        Map<String, Float> weights = new LinkedHashMap<>();
        weights.put("common", 9f);
        weights.put("never", 0f);
        weights.put("rare", 1f);
        for (int i = 0; i < 10000; i++) {
            counts.merge(a.weighted(weights), 1, Integer::sum);
        }
        assertThat(counts.get("common")).isBetween(8700, 9300);
        assertThat(counts).doesNotContainKey("never");
        List<Integer> list = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));
        a.shuffle(list);
        assertThat(list).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6);
        assertThat(a.pick(list)).isBetween(1, 6);
        a.setSeed(7);
        long first = a.nextLong();
        a.setSeed(7);
        assertThat(a.nextLong()).isEqualTo(first);
        assertThat(new Rng().nextFloat()).isBetween(0f, 1f);
        assertThatThrownBy(() -> a.nextInt(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> a.nextInt(5, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> a.pick(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> a.weighted(Map.of("x", 0f))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noiseIsSmoothDeterministicAndBounded() {
        Noise noise = new Noise(99);
        Noise same = new Noise(99);
        Noise other = new Noise(100);
        boolean differs = false;
        for (int i = 0; i < RUNS; i++) {
            float x = rng.nextFloat(-50, 50);
            float y = rng.nextFloat(-50, 50);
            float z = rng.nextFloat(-50, 50);
            for (float value : new float[] {
                noise.perlin(x),
                noise.perlin(x, y),
                noise.perlin(x, y, z),
                noise.simplex(x),
                noise.simplex(x, y),
                noise.simplex(x, y, z),
                noise.fractal(Noise.Type.PERLIN, x, y, 4, 2, 0.5f),
                noise.fractal(Noise.Type.SIMPLEX, x, y, z, 3, 2, 0.5f)
            }) {
                assertThat(value).isBetween(-1.2f, 1.2f);
            }
            assertThat(noise.simplex(x, y)).isEqualTo(same.simplex(x, y));
            differs |= noise.simplex(x, y) != other.simplex(x, y);
            assertThat(Math.abs(noise.perlin(x, y) - noise.perlin(x + 0.001f, y)))
                    .isLessThan(0.02f);
            assertThat(Math.abs(noise.simplex(x, y) - noise.simplex(x + 0.001f, y)))
                    .isLessThan(0.02f);
        }
        assertThat(differs).isTrue();
        assertThat(noise.perlin(1, 2, 3)).as("zero at lattice points").isCloseTo(0f, within(1e-6f));
        assertThat(noise.fractal(Noise.Type.SIMPLEX, 1, 2, 0, 2, 0.5f)).isEqualTo(noise.simplex(1, 2));
        assertThat(noise.fractal(Noise.Type.PERLIN, 1, 2, 3, 0, 2, 0.5f)).isEqualTo(noise.perlin(1, 2, 3));
        Vec2 warped = noise.warp(10, 10, 0.1f, 5);
        assertThat(warped.distanceTo(new Vec2(10, 10))).isLessThanOrEqualTo(5f * 1.5f);
    }

    @Test
    void gridConversionsLinesAndFloodFill() {
        Grid grid = new Grid(2, 2);
        assertThat(grid.cellAt(new Vec2(-0.5f, 3))).isEqualTo(new GridPos(-1, 1));
        assertThat(grid.cellOrigin(new GridPos(1, 2))).isEqualTo(new Vec2(2, 4));
        assertThat(grid.cellCenter(new GridPos(0, 0))).isEqualTo(new Vec2(1, 1));
        assertThat(grid.cellBounds(new GridPos(1, 1))).isEqualTo(Rect.of(2, 2, 2, 2));
        assertThatThrownBy(() -> new Grid(0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Grid.line(new GridPos(0, 0), new GridPos(3, 1)))
                .startsWith(new GridPos(0, 0))
                .endsWith(new GridPos(3, 1))
                .hasSize(4);
        assertThat(Grid.line(new GridPos(2, 2), new GridPos(2, -1))).hasSize(4);
        List<GridPos> room =
                Grid.floodFill(GridPos.ORIGIN, c -> c.x() >= 0 && c.y() >= 0 && c.x() < 3 && c.y() < 2, 100);
        assertThat(room).hasSize(6);
        assertThat(Grid.floodFill(GridPos.ORIGIN, c -> true, 10)).hasSize(10);
        assertThat(Grid.floodFill(GridPos.ORIGIN, c -> false, 10)).isEmpty();
        GridPos p = new GridPos(1, 1);
        assertThat(p.neighbors4()).hasSize(4).contains(new GridPos(1, 0));
        assertThat(p.neighbors8()).hasSize(8).contains(new GridPos(0, 0));
        assertThat(p.manhattan(GridPos.ORIGIN)).isEqualTo(2);
        assertThat(p.chebyshev(new GridPos(4, 2))).isEqualTo(3);
    }

    @Test
    void colorHelpers() {
        assertThat(Color.hex("#ff8800")).isEqualTo(Color.rgb(0xff8800));
        assertThat(Color.hex("f80")).isEqualTo(Color.rgb(0xff8800));
        assertThat(Color.hex("ff880080").a()).isCloseTo(128 / 255f, within(1e-6f));
        assertThatThrownBy(() -> Color.hex("#12")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Color.hex("#zzzzzz")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Color.hsv(0, 1, 1)).isEqualTo(Color.RED);
        assertThat(Color.hsv(120, 1, 1)).isEqualTo(Color.GREEN);
        assertThat(Color.hsv(240, 1, 1)).isEqualTo(Color.BLUE);
        for (float hue : new float[] {30, 90, 150, 210, 270, 330, -30, 720}) {
            Color c = Color.hsv(hue, 0.5f, 0.8f);
            assertThat(Math.max(c.r(), Math.max(c.g(), c.b()))).isCloseTo(0.8f, within(1e-4f));
        }
        assertThat(Color.BLACK.lerp(Color.WHITE, 0.5f).r()).isEqualTo(0.5f);
        assertThat(Color.WHITE.mul(Color.RED)).isEqualTo(Color.RED);
        assertThat(Color.WHITE.withAlpha(0.5f).toPremultipliedAbgr()).isEqualTo(0x80808080);
        assertThat(Color.RED.toPremultipliedAbgr()).isEqualTo(0xff0000ff);
        assertThat(List.of(Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.ORANGE, Color.GRAY))
                .hasSize(5);
    }
}
