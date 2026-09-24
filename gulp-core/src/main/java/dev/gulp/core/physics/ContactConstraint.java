package dev.gulp.core.physics;

import org.jspecify.annotations.Nullable;

/**
 * A persistent contact between a piece of a body and a piece of something else (another body, a collider, a mover or a
 * tile), with the solver data of its points. Impulses carry over between ticks for warm starting.
 */
final class ContactConstraint {

    final BodySim a;
    /** The other body, or {@code null} for static geometry and movers. */
    final @Nullable BodySim b;
    /** The other proxy, or {@code null} for a tile. */
    final @Nullable Proxy other;

    final int tileX;
    final int tileY;
    final int pieceA;
    final int pieceB;

    int count;
    float nx;
    float ny;
    final float[] px = new float[2];
    final float[] py = new float[2];
    final float[] separation = new float[2];
    final int[] id = new int[2];
    final float[] normalImpulse = new float[2];
    final float[] tangentImpulse = new float[2];
    final float[] maxNormalImpulse = new float[2];

    // Solver data, set in prepare.
    final float[] rax = new float[2];
    final float[] ray = new float[2];
    final float[] rbx = new float[2];
    final float[] rby = new float[2];
    final float[] adjustedSeparation = new float[2];
    final float[] normalMass = new float[2];
    final float[] tangentMass = new float[2];
    final float[] relativeVelocity = new float[2];
    float friction;
    float restitution;
    /** Velocity of the other side when it is not a body: movers push with their own velocity. */
    float otherVx;

    float otherVy;

    boolean seen;
    boolean touching;
    boolean wasTouching;
    boolean disabled;

    ContactConstraint(
            BodySim a, @Nullable BodySim b, @Nullable Proxy other, int tileX, int tileY, int pieceA, int pieceB) {
        this.a = a;
        this.b = b;
        this.other = other;
        this.tileX = tileX;
        this.tileY = tileY;
        this.pieceA = pieceA;
        this.pieceB = pieceB;
    }

    /** Copies a new manifold in, carrying impulses over to points with matching features. */
    void update(Manifold m) {
        float oldN0 = normalImpulse[0];
        float oldN1 = normalImpulse[1];
        float oldT0 = tangentImpulse[0];
        float oldT1 = tangentImpulse[1];
        int oldId0 = id[0];
        int oldId1 = id[1];
        int oldCount = count;
        count = m.count;
        nx = m.nx;
        ny = m.ny;
        for (int i = 0; i < m.count; i++) {
            px[i] = m.px[i];
            py[i] = m.py[i];
            separation[i] = m.separation[i];
            id[i] = m.id[i];
            normalImpulse[i] = 0f;
            tangentImpulse[i] = 0f;
            if (oldCount > 0 && m.id[i] == oldId0) {
                normalImpulse[i] = oldN0;
                tangentImpulse[i] = oldT0;
            } else if (oldCount > 1 && m.id[i] == oldId1) {
                normalImpulse[i] = oldN1;
                tangentImpulse[i] = oldT1;
            }
        }
    }

    float minSeparation() {
        float best = Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            best = Math.min(best, separation[i]);
        }
        return best;
    }
}
