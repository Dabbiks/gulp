package dev.gulp.core.physics;

/** Up to two contact points between two convex pieces, with a normal pointing from the first piece to the second. */
final class Manifold {

    int count;
    float nx;
    float ny;
    final float[] px = new float[2];
    final float[] py = new float[2];
    /** Signed distance between the surfaces at each point; negative when overlapping. */
    final float[] separation = new float[2];
    /** Feature keys for matching points between ticks. */
    final int[] id = new int[2];

    float minSeparation() {
        float best = Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            best = Math.min(best, separation[i]);
        }
        return best;
    }

    void add(float x, float y, float s, int key) {
        px[count] = x;
        py[count] = y;
        separation[count] = s;
        id[count] = key;
        count++;
    }

    void flip() {
        nx = -nx;
        ny = -ny;
    }
}
