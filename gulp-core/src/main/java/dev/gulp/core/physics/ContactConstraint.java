package dev.gulp.core.physics;

import org.jspecify.annotations.Nullable;

/**
 * A persistent contact between a piece of a body and a piece of something else (another body, a collider, a mover or a
 * tile), with the solver data of its points. Impulses carry over between ticks for warm starting.
 */
final class ContactConstraint {

    BodySim a;
    /** The other body, or {@code null} for static geometry and movers. */
    @Nullable BodySim b;
    /** The other proxy, or {@code null} for a tile. */
    @Nullable Proxy other;

    int tileX;
    int tileY;
    int pieceA;
    int pieceB;

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

    /** Position in the contact list of the world, for removal. */
    int index;

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

    /** Makes a pooled constraint new again for another pair. */
    void reset(BodySim a, @Nullable BodySim b, @Nullable Proxy other, int tileX, int tileY, int pieceA, int pieceB) {
        this.a = a;
        this.b = b;
        this.other = other;
        this.tileX = tileX;
        this.tileY = tileY;
        this.pieceA = pieceA;
        this.pieceB = pieceB;
        count = 0;
        nx = 0f;
        ny = 0f;
        for (int i = 0; i < 2; i++) {
            px[i] = 0f;
            py[i] = 0f;
            separation[i] = 0f;
            id[i] = 0;
            normalImpulse[i] = 0f;
            tangentImpulse[i] = 0f;
            maxNormalImpulse[i] = 0f;
        }
        friction = 0f;
        restitution = 0f;
        otherVx = 0f;
        otherVy = 0f;
        seen = false;
        touching = false;
        wasTouching = false;
        disabled = false;
    }

    /** Drops references to bodies and proxies while the constraint waits in the pool. */
    void release() {
        b = null;
        other = null;
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
