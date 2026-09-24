package dev.gulp.core.physics;

/** A {@link Convex} placed in the world: rotated, moved and bounded. Reused as scratch, so collisions allocate nothing. */
final class Placed {

    int count;
    float radius;
    final float[] x = new float[Convex.MAX_VERTICES];
    final float[] y = new float[Convex.MAX_VERTICES];
    final float[] nx = new float[Convex.MAX_VERTICES];
    final float[] ny = new float[Convex.MAX_VERTICES];
    float minX;
    float minY;
    float maxX;
    float maxY;

    Placed set(Convex piece, float px, float py, float cos, float sin) {
        count = piece.count;
        radius = piece.radius;
        minX = Float.MAX_VALUE;
        minY = Float.MAX_VALUE;
        maxX = -Float.MAX_VALUE;
        maxY = -Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            float wx = cos * piece.x[i] - sin * piece.y[i] + px;
            float wy = sin * piece.x[i] + cos * piece.y[i] + py;
            x[i] = wx;
            y[i] = wy;
            minX = Math.min(minX, wx);
            minY = Math.min(minY, wy);
            maxX = Math.max(maxX, wx);
            maxY = Math.max(maxY, wy);
        }
        int edges = piece.edges();
        for (int i = 0; i < edges; i++) {
            nx[i] = cos * piece.nx[i] - sin * piece.ny[i];
            ny[i] = sin * piece.nx[i] + cos * piece.ny[i];
        }
        minX -= radius;
        minY -= radius;
        maxX += radius;
        maxY += radius;
        return this;
    }

    Placed set(Convex piece, float px, float py) {
        return set(piece, px, py, 1f, 0f);
    }

    Placed copy(Placed other) {
        count = other.count;
        radius = other.radius;
        System.arraycopy(other.x, 0, x, 0, count);
        System.arraycopy(other.y, 0, y, 0, count);
        System.arraycopy(other.nx, 0, nx, 0, count);
        System.arraycopy(other.ny, 0, ny, 0, count);
        minX = other.minX;
        minY = other.minY;
        maxX = other.maxX;
        maxY = other.maxY;
        return this;
    }

    void move(float dx, float dy) {
        for (int i = 0; i < count; i++) {
            x[i] += dx;
            y[i] += dy;
        }
        minX += dx;
        maxX += dx;
        minY += dy;
        maxY += dy;
    }

    int edges() {
        return count == 1 ? 0 : count;
    }

    boolean overlapsBox(float left, float top, float right, float bottom) {
        return maxX >= left && minX <= right && maxY >= top && minY <= bottom;
    }
}
