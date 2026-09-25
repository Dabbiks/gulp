package dev.gulp.api.ui;

/**
 * Keeps one child at a width-to-height ratio: the largest rectangle of that ratio fitting the space, centred. Created
 * by {@link Ui#aspect}. Theme type: {@code aspect}.
 *
 * <pre>{@code
 * aspect(16f / 9f, image(GameAssets.Textures.PREVIEW)).grow();
 * }</pre>
 */
public final class Aspect extends Container<Aspect> {

    private final float ratio;

    /**
     * Creates an aspect container.
     *
     * @param ratio width divided by height
     * @param child the child
     * @throws IllegalArgumentException if the ratio is not positive
     */
    public Aspect(float ratio, Node<?> child) {
        super(child);
        if (!(ratio > 0f)) {
            throw new IllegalArgumentException("Aspect ratio must be positive");
        }
        this.ratio = ratio;
    }

    @Override
    protected String styleType() {
        return "aspect";
    }

    /**
     * Returns the ratio.
     *
     * @return width divided by height
     */
    public float ratio() {
        return ratio;
    }

    @Override
    protected Size measure() {
        float w = maxMin(true);
        float h = maxMin(false);
        if (w / Math.max(h, 0.001f) > ratio) {
            h = w / ratio;
        } else {
            w = h * ratio;
        }
        return new Size(w, h);
    }

    @Override
    protected void arrange() {
        float w = width;
        float h = w / ratio;
        if (h > height) {
            h = height;
            w = h * ratio;
        }
        for (int i = 0; i < children.size(); i++) {
            Node<?> child = children.get(i);
            if (child.isVisible()) {
                place(child, x + (width - w) / 2f, y + (height - h) / 2f, w, h);
            }
        }
    }
}
