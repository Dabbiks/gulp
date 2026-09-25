package dev.gulp.api.ui;

import dev.gulp.api.Gulp;
import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.asset.Assets;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.render.Draw;
import org.jspecify.annotations.Nullable;

/**
 * A texture region, sized by default to its pixels (one pixel per UI point) and fitted into the space it gets. A key
 * that is not loaded yet loads in the background. {@link Node#tint} colours it. Created by {@link Ui#image}. Theme
 * type: {@code image}.
 *
 * <pre>{@code
 * image(GameAssets.Sprites.COIN_ICON).size(16, 16);
 * image(portrait).scaling(Image.Scaling.STRETCH).grow();
 * }</pre>
 */
public final class Image extends Node<Image> {

    /** How the region fills the node. */
    public enum Scaling {
        /** As large as fits, keeping proportions, centred. */
        FIT,
        /** Stretched over the whole node. */
        STRETCH,
        /** At its pixel size, centred. */
        NONE
    }

    private @Nullable TextureRegion region;
    private @Nullable AssetKey<TextureRegion> key;
    private Scaling scaling = Scaling.FIT;

    /**
     * Creates an image of a region.
     *
     * @param region the region
     */
    public Image(TextureRegion region) {
        this.region = region;
    }

    /**
     * Creates an image of a region asset, loading it if needed.
     *
     * @param key the region key
     */
    public Image(AssetKey<TextureRegion> key) {
        this.key = key;
    }

    @Override
    protected String styleType() {
        return "image";
    }

    @Override
    protected boolean usesPadding() {
        return false;
    }

    /**
     * Sets how the region fills the node.
     *
     * @param value the scaling
     * @return this image
     */
    public Image scaling(Scaling value) {
        this.scaling = value;
        return this;
    }

    /**
     * Shows another region.
     *
     * @param value the region
     * @return this image
     */
    public Image region(TextureRegion value) {
        this.region = value;
        this.key = null;
        invalidate();
        return this;
    }

    /**
     * Returns the region.
     *
     * @return the region, or {@code null} while loading
     */
    public @Nullable TextureRegion region() {
        return region;
    }

    @Override
    protected void mounted() {
        AssetKey<TextureRegion> wanted = key;
        if (wanted != null && region == null) {
            Assets assets = Gulp.engine().assets();
            if (assets.isLoaded(wanted)) {
                region = assets.get(wanted);
            } else {
                assets.load(wanted).thenSync(loaded -> {
                    if (wanted.equals(key)) {
                        region = loaded;
                        invalidate();
                    }
                });
            }
        }
    }

    @Override
    protected Size measure() {
        TextureRegion current = region;
        return current == null ? Size.ZERO : new Size(current.width(), current.height());
    }

    @Override
    protected void draw(Draw draw) {
        TextureRegion current = region;
        if (current == null || width <= 0f || height <= 0f) {
            return;
        }
        float w = width;
        float h = height;
        if (scaling == Scaling.FIT) {
            float factor = Math.min(width / Math.max(1f, current.width()), height / Math.max(1f, current.height()));
            w = current.width() * factor;
            h = current.height() * factor;
        } else if (scaling == Scaling.NONE) {
            w = current.width();
            h = current.height();
        }
        Color previous = draw.color();
        draw.color(tint());
        draw.image(current, x + (width - w) / 2f, y + (height - h) / 2f, w, h);
        draw.color(previous);
    }
}
