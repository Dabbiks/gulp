package dev.gulp.api.ui;

/**
 * A direction of focus movement; the Y axis points down, as everywhere in Gulp.
 *
 * <pre>{@code
 * playButton.focusNeighbor(Direction.DOWN, quitButton);
 * }</pre>
 */
public enum Direction {
    /** Towards the top. */
    UP(0, -1),
    /** Towards the bottom. */
    DOWN(0, 1),
    /** Towards the left. */
    LEFT(-1, 0),
    /** Towards the right. */
    RIGHT(1, 0);

    private final int dx;
    private final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    /**
     * Returns the horizontal step.
     *
     * @return -1, 0 or 1
     */
    public int dx() {
        return dx;
    }

    /**
     * Returns the vertical step.
     *
     * @return -1, 0 or 1
     */
    public int dy() {
        return dy;
    }
}
