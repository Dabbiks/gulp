package dev.gulp.api.entity.component;

import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.entity.Component;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.math.Vec2;
import org.jspecify.annotations.Nullable;

/**
 * Draws an image for the entity, in the entity's render layer, with its rotation, scale, flip and tint. By default the
 * image is centred on the entity and sized at its pixel size divided by the world tile size, so a 16-pixel sprite in a
 * world of 16-pixel tiles is one unit wide. The region loads when the entity spawns.
 *
 * <pre>{@code
 * EntityType.builder(key("coin")).component(() -> new SpriteComponent(GameAssets.Sprites.COIN)).build();
 * entity.get(SpriteComponent.class).setRegion(GameAssets.Sprites.COIN_SHINE);
 * }</pre>
 */
public final class SpriteComponent extends Component {

    private @Nullable AssetKey<TextureRegion> key;
    private @Nullable TextureRegion region;
    private @Nullable Vec2 size;
    private Vec2 anchor = new Vec2(0.5f, 0.5f);
    private Vec2 offset = Vec2.ZERO;
    private Color tint = Color.WHITE;

    /**
     * Creates a sprite from an atlas region or texture key.
     *
     * @param region the region key
     */
    public SpriteComponent(AssetKey<TextureRegion> region) {
        this.key = region;
    }

    /**
     * Creates a sprite from a region in memory.
     *
     * @param region the region
     */
    public SpriteComponent(TextureRegion region) {
        this.region = region;
    }

    @Override
    protected void onSpawn() {
        AssetKey<TextureRegion> current = key;
        if (current != null && region == null) {
            if (assets().isLoaded(current)) {
                region = assets().get(current);
            } else {
                assets().load(current).thenSync(loaded -> {
                    if (current.equals(key)) {
                        region = loaded;
                    }
                });
            }
        }
    }

    /**
     * Returns the image.
     *
     * @return the region, or {@code null} while it loads
     */
    public @Nullable TextureRegion region() {
        return region;
    }

    /**
     * Changes the image to a loaded region.
     *
     * @param value the region
     * @return this component
     */
    public SpriteComponent setRegion(TextureRegion value) {
        this.key = null;
        this.region = value;
        return this;
    }

    /**
     * Changes the image to a region key, loading it if needed; the old image shows until then.
     *
     * @param value the region key
     * @return this component
     */
    public SpriteComponent setRegion(AssetKey<TextureRegion> value) {
        this.key = value;
        if (isAttached() && entity().isSpawned()) {
            onSpawn();
            if (assets().isLoaded(value)) {
                region = assets().get(value);
            }
        }
        return this;
    }

    /**
     * Returns the drawn size.
     *
     * @return world units, or {@code null} for the pixel size divided by the tile size
     */
    public @Nullable Vec2 size() {
        return size;
    }

    /**
     * Sets the drawn size.
     *
     * @param width world units
     * @param height world units
     * @return this component
     */
    public SpriteComponent setSize(float width, float height) {
        this.size = new Vec2(width, height);
        return this;
    }

    /**
     * Returns the point of the image placed on the entity position.
     *
     * @return {@code 0..1} on both axes, the centre by default
     */
    public Vec2 anchor() {
        return anchor;
    }

    /**
     * Sets the point of the image placed on the entity position; {@code (0.5, 1)} stands the image on the position.
     *
     * @param x {@code 0} left, {@code 1} right
     * @param y {@code 0} top, {@code 1} bottom
     * @return this component
     */
    public SpriteComponent setAnchor(float x, float y) {
        this.anchor = new Vec2(x, y);
        return this;
    }

    /**
     * Returns the offset from the entity position.
     *
     * @return world units
     */
    public Vec2 offset() {
        return offset;
    }

    /**
     * Shifts the image.
     *
     * @param x world units
     * @param y world units
     * @return this component
     */
    public SpriteComponent setOffset(float x, float y) {
        this.offset = new Vec2(x, y);
        return this;
    }

    /**
     * Returns the tint, multiplied with the entity tint.
     *
     * @return the colour
     */
    public Color tint() {
        return tint;
    }

    /**
     * Tints the image.
     *
     * @param value the colour
     * @return this component
     */
    public SpriteComponent setTint(Color value) {
        this.tint = value;
        return this;
    }
}
