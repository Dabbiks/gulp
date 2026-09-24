package dev.gulp.core.world;

import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.world.Parallax;
import dev.gulp.api.world.ParallaxLayer;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** {@link Parallax}: background layers drawn by {@link WorldRenderer}. */
final class ParallaxImpl implements Parallax {

    private final WorldImpl world;
    final List<LayerImpl> layers = new ArrayList<>();

    ParallaxImpl(WorldImpl world) {
        this.world = world;
    }

    @Override
    public ParallaxLayer layer(AssetKey<Texture> texture, float factor) {
        LayerImpl layer = new LayerImpl(texture, factor);
        layers.add(layer);
        if (world.worlds.assets.isLoaded(texture)) {
            layer.texture = world.worlds.assets.get(texture);
        } else {
            world.worlds.assets.load(texture).thenSync(loaded -> layer.texture = loaded);
        }
        return layer;
    }

    @Override
    public List<ParallaxLayer> layers() {
        return List.copyOf(layers);
    }

    @Override
    public void clear() {
        layers.clear();
    }

    void advance(float seconds) {
        for (int i = 0; i < layers.size(); i++) {
            LayerImpl layer = layers.get(i);
            layer.scrolledX += layer.speedX * seconds;
            layer.scrolledY += layer.speedY * seconds;
        }
    }

    /** One background image. */
    final class LayerImpl implements ParallaxLayer {
        final AssetKey<Texture> key;

        @Nullable Texture texture;

        float factorX;
        float factorY;
        boolean repeatX;
        boolean repeatY;
        float speedX;
        float speedY;
        float scrolledX;
        float scrolledY;
        float offsetX;
        float offsetY;
        float scale = 1f;
        Color tint = Color.WHITE;
        String renderLayer = "background";

        LayerImpl(AssetKey<Texture> key, float factor) {
            this.key = key;
            this.factorX = factor;
            this.factorY = factor;
        }

        @Override
        public ParallaxLayer factor(float x, float y) {
            factorX = x;
            factorY = y;
            return this;
        }

        @Override
        public ParallaxLayer repeatX() {
            repeatX = true;
            return this;
        }

        @Override
        public ParallaxLayer repeatY() {
            repeatY = true;
            return this;
        }

        @Override
        public ParallaxLayer autoScroll(float unitsPerSecondX, float unitsPerSecondY) {
            speedX = unitsPerSecondX;
            speedY = unitsPerSecondY;
            return this;
        }

        @Override
        public ParallaxLayer offset(float x, float y) {
            offsetX = x;
            offsetY = y;
            return this;
        }

        @Override
        public ParallaxLayer scale(float factor) {
            scale = factor;
            return this;
        }

        @Override
        public ParallaxLayer tint(Color color) {
            tint = color;
            return this;
        }

        @Override
        public ParallaxLayer renderLayer(String name) {
            renderLayer = name;
            return this;
        }

        @Override
        public void remove() {
            layers.remove(this);
        }
    }
}
