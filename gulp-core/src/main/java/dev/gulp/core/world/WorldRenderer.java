package dev.gulp.core.world;

import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.entity.component.SpriteComponent;
import dev.gulp.api.entity.component.WorldText;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Mesh2D;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.math.Rect;
import dev.gulp.api.render.RenderLayer;
import dev.gulp.api.text.TextAlign;
import dev.gulp.api.world.Chunk;
import dev.gulp.api.world.TileSet;
import dev.gulp.api.world.TileType;
import dev.gulp.core.asset.AssetsImpl;
import dev.gulp.core.graphics.CameraImpl;
import dev.gulp.core.graphics.DrawImpl;
import dev.gulp.core.util.IntList;
import dev.gulp.core.util.LongObjectMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Draws the built-in contents of a world layer: parallax images, tile layers from per-chunk mesh caches (animated tiles
 * drawn one by one), and entity sprites sorted by z-index and position. Also draws {@link WorldText} in screen space.
 * Nothing here allocates per frame once caches are warm.
 */
final class WorldRenderer {

    private final AssetsImpl assets;
    private final Map<TileType, TileVisual> visuals = new HashMap<>();
    private final IdentityHashMap<TextureRegion, TextureRegion[]> flipped = new IdentityHashMap<>();
    private final float[] view = new float[4];
    private EntityImpl[] sprites = new EntityImpl[64];
    private EntityImpl[] sortScratch = new EntityImpl[64];
    private int spriteCount;
    private @Nullable String collectLayer;
    private final Consumer<EntityImpl> collector = this::collect;
    private int visualsVersion;
    private float time;

    WorldRenderer(AssetsImpl assets) {
        this.assets = assets;
    }

    void advance(float seconds) {
        time += seconds;
    }

    // ------------------------------------------------------------------ tile visuals

    /** Resolved frames of a tile type; empty while its images load. */
    private static final class TileVisual {
        @Nullable TextureRegion[] frames;

        boolean requested;
    }

    private @Nullable TextureRegion[] frames(TileType type) {
        TileVisual visual = visuals.get(type);
        if (visual == null) {
            visual = new TileVisual();
            visuals.put(type, visual);
        }
        if (visual.frames != null) {
            return visual.frames;
        }
        TextureRegion direct = type.directRegion();
        if (direct != null) {
            visual.frames = new TextureRegion[] {direct};
            return visual.frames;
        }
        TileSet set = type.tileSet();
        if (set != null) {
            Texture texture = assets.getIfLoaded(set.texture());
            if (texture == null) {
                request(visual, set.texture());
                return null;
            }
            int[] indices = type.tileIndices();
            TextureRegion[] result = new TextureRegion[indices.length];
            for (int i = 0; i < indices.length; i++) {
                int[] origin = set.origin(indices[i], texture.width());
                result[i] = new TextureRegion(texture, origin[0], origin[1], set.tileWidth(), set.tileHeight());
            }
            visual.frames = result;
            return result;
        }
        List<AssetKey<TextureRegion>> keys = type.regions();
        if (keys.isEmpty()) {
            visual.frames = new TextureRegion[0];
            return visual.frames;
        }
        TextureRegion[] result = new TextureRegion[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            TextureRegion region = assets.getIfLoaded(keys.get(i));
            if (region == null) {
                request(visual, keys.get(i));
                return null;
            }
            result[i] = region;
        }
        visual.frames = result;
        return result;
    }

    private void request(TileVisual visual, AssetKey<?> key) {
        if (!visual.requested) {
            visual.requested = true;
            assets.load(key).thenSync(loaded -> {
                visual.requested = false;
                visualsVersion++;
            });
        }
    }

    // ------------------------------------------------------------------ drawing

    void drawLayer(WorldImpl world, DrawImpl draw, RenderLayer layer, CameraImpl camera, float alpha) {
        camera.viewBounds(view);
        String name = layer.name();
        drawParallax(world, draw, name, camera);
        drawTiles(world, draw, name, camera);
        drawSprites(world, draw, name, alpha);
    }

    private void drawParallax(WorldImpl world, DrawImpl draw, String name, CameraImpl camera) {
        List<ParallaxImpl.LayerImpl> layers = world.parallax.layers;
        int tileSize = world.settings().tileSize();
        for (int i = 0; i < layers.size(); i++) {
            ParallaxImpl.LayerImpl layer = layers.get(i);
            Texture texture = layer.texture;
            if (texture == null || !layer.renderLayer.equals(name)) {
                continue;
            }
            float width = texture.width() / (float) tileSize * layer.scale;
            float height = texture.height() / (float) tileSize * layer.scale;
            float cameraX = camera.position().x();
            float cameraY = camera.position().y();
            float originX = layer.offsetX + layer.scrolledX + cameraX * (1f - layer.factorX);
            float originY = layer.offsetY + layer.scrolledY + cameraY * (1f - layer.factorY);
            float startX = originX;
            float endX = originX + width;
            if (layer.repeatX) {
                startX = originX + (float) Math.floor((view[0] - originX) / width) * width;
                endX = view[2];
            }
            float startY = originY;
            float endY = originY + height;
            if (layer.repeatY) {
                startY = originY + (float) Math.floor((view[1] - originY) / height) * height;
                endY = view[3];
            }
            draw.color(layer.tint);
            for (float y = startY; y < endY; y += height) {
                for (float x = startX; x < endX; x += width) {
                    draw.image(texture.region(), x, y, width, height);
                }
                if (!layer.repeatY) {
                    break;
                }
            }
        }
        draw.color(Color.WHITE);
    }

    private void drawTiles(WorldImpl world, DrawImpl draw, String name, CameraImpl camera) {
        float cameraX = camera.position().x();
        float cameraY = camera.position().y();
        TileMapImpl map = world.tileMap;
        List<TileMapImpl.TileLayerImpl> layers = map.layers;
        float unitHeight = map.unitHeight();
        int cx0 = Math.floorDiv((int) Math.floor(view[0]) - 2, Chunk.SIZE);
        int cx1 = Math.floorDiv((int) Math.floor(view[2]) + 2, Chunk.SIZE);
        int cy0 = Math.floorDiv((int) Math.floor(view[1] / unitHeight) - 2, Chunk.SIZE);
        int cy1 = Math.floorDiv((int) Math.floor(view[3] / unitHeight) + 2, Chunk.SIZE);
        boolean orthogonal = map.orientation() == dev.gulp.api.world.TileOrientation.ORTHOGONAL;
        for (int l = 0; l < layers.size(); l++) {
            TileMapImpl.TileLayerImpl layer = layers.get(l);
            if (!layer.visible || !layer.renderLayer.equals(name)) {
                continue;
            }
            float parallaxX = layer.parallax.x();
            float parallaxY = layer.parallax.y();
            boolean shifted = parallaxX != 1f || parallaxY != 1f;
            if (shifted) {
                // A tile layer with parallax moves by a fraction of the camera motion.
                draw.push().translate(cameraX * (1f - parallaxX), cameraY * (1f - parallaxY));
            }
            if (orthogonal && !shifted) {
                for (int cy = cy0; cy <= cy1; cy++) {
                    for (int cx = cx0; cx <= cx1; cx++) {
                        ChunkImpl chunk = map.chunks.get(LongObjectMap.pack(cx, cy));
                        if (chunk != null) {
                            drawChunk(map, draw, chunk, layer);
                        }
                    }
                }
            } else {
                // Diamond and hexagonal layouts do not map screen rectangles to chunk rectangles; draw every loaded
                // chunk
                // whose tiles can reach the view.
                for (int i = 0; i < map.loaded.size(); i++) {
                    drawChunk(map, draw, map.loaded.get(i), layer);
                }
            }
            if (shifted) {
                draw.pop();
            }
        }
        draw.color(Color.WHITE);
    }

    /** Cached geometry of one layer of one chunk. */
    private static final class ChunkMesh {
        int chunkVersion = -1;
        int layerVersion = -1;
        int visuals = -1;
        final List<Texture> textures = new ArrayList<>(2);
        final List<Mesh2D> meshes = new ArrayList<>(2);
        final IntList animated = new IntList();
    }

    private void drawChunk(TileMapImpl map, DrawImpl draw, ChunkImpl chunk, TileMapImpl.TileLayerImpl layer) {
        if (layer.index >= chunk.cells.length || chunk.cells[layer.index] == null) {
            return;
        }
        Object cached = chunk.cache(layer.index);
        ChunkMesh mesh;
        if (cached instanceof ChunkMesh existing) {
            mesh = existing;
        } else {
            mesh = new ChunkMesh();
            chunk.setCache(layer.index, mesh);
        }
        if (mesh.chunkVersion != chunk.version
                || mesh.layerVersion != layer.version
                || mesh.visuals != visualsVersion) {
            build(map, chunk, layer, mesh);
        }
        draw.color(Color.WHITE);
        for (int i = 0; i < mesh.meshes.size(); i++) {
            draw.mesh(mesh.meshes.get(i), mesh.textures.get(i));
        }
        IntList animated = mesh.animated;
        if (!animated.isEmpty()) {
            int[] cells = chunk.cells[layer.index];
            draw.color(layer.tint);
            for (int i = 0; i < animated.size(); i++) {
                int local = animated.get(i);
                int cell = cells[local];
                TileType type = map.typeOf(cell);
                TextureRegion[] frames = type == null ? null : frames(type);
                if (frames == null || frames.length == 0) {
                    continue;
                }
                int frame = (int) (time / Math.max(0.001f, type.frameSeconds())) % frames.length;
                TextureRegion region = oriented(frames[frame], cell);
                int x = chunk.cx * Chunk.SIZE + local % Chunk.SIZE;
                int y = chunk.cy * Chunk.SIZE + local / Chunk.SIZE;
                float width = region.width() / (float) map.tileWidth();
                float height = region.height() / (float) map.tileWidth();
                float left = map.centerX(x, y) - 0.5f;
                float bottom = map.centerY(x, y) + map.unitHeight() / 2f;
                draw.image(region, left, bottom - height, width, height);
            }
        }
    }

    private void build(TileMapImpl map, ChunkImpl chunk, TileMapImpl.TileLayerImpl layer, ChunkMesh mesh) {
        mesh.chunkVersion = chunk.version;
        mesh.layerVersion = layer.version;
        mesh.visuals = visualsVersion;
        for (Mesh2D existing : mesh.meshes) {
            existing.clear();
        }
        mesh.animated.clear();
        int[] cells = chunk.cells[layer.index];
        Color tint = layer.tint;
        float unitHeight = map.unitHeight();
        for (int local = 0; local < ChunkImpl.CELLS; local++) {
            int cell = cells[local];
            if (cell == 0) {
                continue;
            }
            TileType type = map.typeOf(cell);
            if (type == null) {
                continue;
            }
            TextureRegion[] frames = frames(type);
            if (frames == null || frames.length == 0) {
                continue;
            }
            if (frames.length > 1) {
                mesh.animated.add(local);
                continue;
            }
            TextureRegion region = frames[0];
            int x = chunk.cx * Chunk.SIZE + local % Chunk.SIZE;
            int y = chunk.cy * Chunk.SIZE + local / Chunk.SIZE;
            float width = region.width() / (float) map.tileWidth();
            float height = region.height() / (float) map.tileWidth();
            float left = map.centerX(x, y) - 0.5f;
            float bottom = map.centerY(x, y) + unitHeight / 2f;
            Mesh2D target = meshFor(mesh, region.texture());
            float u = region.u();
            float v = region.v();
            float u2 = region.u2();
            float v2 = region.v2();
            float[] us = {u, u2, u2, u};
            float[] vs = {v, v, v2, v2};
            orient(us, vs, cell);
            int a = target.vertex(left, bottom - height, us[0], vs[0], tint);
            int b = target.vertex(left + width, bottom - height, us[1], vs[1], tint);
            int c = target.vertex(left + width, bottom, us[2], vs[2], tint);
            int d = target.vertex(left, bottom, us[3], vs[3], tint);
            target.triangle(a, b, c).triangle(a, c, d);
        }
        for (int i = mesh.meshes.size() - 1; i >= 0; i--) {
            if (mesh.meshes.get(i).vertexCount() == 0) {
                mesh.meshes.remove(i);
                mesh.textures.remove(i);
            }
        }
    }

    /** Applies the flip flags of a cell to the corner texture coordinates (top-left, top-right, bottom-right, bottom-left). */
    private static void orient(float[] us, float[] vs, int cell) {
        if ((cell & ChunkImpl.FLIP_D) != 0) {
            swap(us, vs, 1, 3);
        }
        if ((cell & ChunkImpl.FLIP_H) != 0) {
            swap(us, vs, 0, 1);
            swap(us, vs, 3, 2);
        }
        if ((cell & ChunkImpl.FLIP_V) != 0) {
            swap(us, vs, 0, 3);
            swap(us, vs, 1, 2);
        }
    }

    private static void swap(float[] us, float[] vs, int a, int b) {
        float u = us[a];
        us[a] = us[b];
        us[b] = u;
        float v = vs[a];
        vs[a] = vs[b];
        vs[b] = v;
    }

    private TextureRegion oriented(TextureRegion region, int cell) {
        boolean h = (cell & ChunkImpl.FLIP_H) != 0;
        boolean v = (cell & ChunkImpl.FLIP_V) != 0;
        return h || v ? flip(region, h, v) : region;
    }

    private static Mesh2D meshFor(ChunkMesh mesh, Texture texture) {
        for (int i = 0; i < mesh.textures.size(); i++) {
            if (mesh.textures.get(i) == texture) {
                return mesh.meshes.get(i);
            }
        }
        Mesh2D created = new Mesh2D();
        mesh.textures.add(texture);
        mesh.meshes.add(created);
        return created;
    }

    private TextureRegion flip(TextureRegion region, boolean x, boolean y) {
        TextureRegion[] variants = flipped.get(region);
        if (variants == null) {
            variants = new TextureRegion[] {
                region.flipX(), region.flipY(), region.flipX().flipY()
            };
            flipped.put(region, variants);
        }
        return x && y ? variants[2] : x ? variants[0] : variants[1];
    }

    // ------------------------------------------------------------------ sprites

    private void collect(EntityImpl entity) {
        if (!entity.visible || entity.removed || !entity.layer.equals(collectLayer)) {
            return;
        }
        SpriteComponent sprite = entity.component(SpriteComponent.class);
        if (sprite == null || sprite.region() == null) {
            return;
        }
        if (spriteCount == sprites.length) {
            sprites = Arrays.copyOf(sprites, spriteCount * 2);
        }
        sprites[spriteCount++] = entity;
    }

    private void drawSprites(WorldImpl world, DrawImpl draw, String name, float alpha) {
        ComponentStore store = world.store(SpriteComponent.class);
        if (store == null || store.size == 0) {
            return;
        }
        spriteCount = 0;
        collectLayer = name;
        // Sprites can reach past their bounds; widen the view a little.
        world.grid.query(view[0] - 4f, view[1] - 4f, view[2] + 4f, view[3] + 4f, collector);
        collectLayer = null;
        if (spriteCount == 0) {
            return;
        }
        sort(spriteCount);
        int tileSize = world.settings().tileSize();
        for (int i = 0; i < spriteCount; i++) {
            EntityImpl entity = sprites[i];
            sprites[i] = null;
            SpriteComponent sprite = entity.component(SpriteComponent.class);
            TextureRegion region = sprite == null ? null : sprite.region();
            if (region == null) {
                continue;
            }
            float fullWidth;
            float fullHeight;
            if (sprite.size() != null) {
                fullWidth = sprite.size().x();
                fullHeight = sprite.size().y();
            } else {
                fullWidth = region.originalWidth() / (float) tileSize;
                fullHeight = region.originalHeight() / (float) tileSize;
            }
            fullWidth *= Math.abs(entity.scaleX);
            fullHeight *= Math.abs(entity.scaleY);
            boolean flipX = entity.flipX ^ entity.scaleX < 0f;
            boolean flipY = entity.flipY ^ entity.scaleY < 0f;
            float anchorX = sprite.anchor().x();
            float anchorY = sprite.anchor().y();
            float x = entity.renderX(alpha) + sprite.offset().x();
            float y = entity.renderY(alpha) + sprite.offset().y();
            float left = x - anchorX * fullWidth;
            float top = y - anchorY * fullHeight;
            // Trimmed atlas regions: place the stored pixels inside the original frame.
            float scaleX = fullWidth / region.originalWidth();
            float scaleY = fullHeight / region.originalHeight();
            float offsetX = region.offsetX() * scaleX;
            float offsetY = region.offsetY() * scaleY;
            float width = region.width() * scaleX;
            float height = region.height() * scaleY;
            if (flipX) {
                offsetX = fullWidth - offsetX - width;
            }
            if (flipY) {
                offsetY = fullHeight - offsetY - height;
            }
            Color tint = sprite.tint() == Color.WHITE ? entity.tint : entity.tint.mul(sprite.tint());
            draw.color(tint);
            draw.image(
                    flipX || flipY ? flip(region, flipX, flipY) : region,
                    left + offsetX,
                    top + offsetY,
                    width,
                    height,
                    x - left - offsetX,
                    y - top - offsetY,
                    entity.renderRotation(alpha));
        }
        draw.color(Color.WHITE);
    }

    /** Stable merge sort by z-index, then y, then spawn order; no allocation after warm-up. */
    private void sort(int count) {
        if (sortScratch.length < count) {
            sortScratch = new EntityImpl[sprites.length];
        }
        for (int width = 1; width < count; width *= 2) {
            for (int start = 0; start < count; start += width * 2) {
                int middle = Math.min(start + width, count);
                int end = Math.min(start + width * 2, count);
                if (middle >= end || !before(sprites[middle], sprites[middle - 1])) {
                    continue;
                }
                int i = start;
                int j = middle;
                int k = start;
                while (i < middle && j < end) {
                    sortScratch[k++] = before(sprites[j], sprites[i]) ? sprites[j++] : sprites[i++];
                }
                while (i < middle) {
                    sortScratch[k++] = sprites[i++];
                }
                while (j < end) {
                    sortScratch[k++] = sprites[j++];
                }
                System.arraycopy(sortScratch, start, sprites, start, end - start);
            }
        }
        Arrays.fill(sortScratch, 0, count, null);
    }

    private static boolean before(EntityImpl a, EntityImpl b) {
        if (a.zIndex != b.zIndex) {
            return a.zIndex < b.zIndex;
        }
        float ay = a.y + a.height / 2f;
        float by = b.y + b.height / 2f;
        if (ay != by) {
            return ay < by;
        }
        return a.runtimeId < b.runtimeId;
    }

    // ------------------------------------------------------------------ overlay

    void drawOverlay(WorldImpl world, DrawImpl draw, CameraImpl camera, float alpha) {
        ComponentStore store = world.store(WorldText.class);
        if (store == null || store.size == 0) {
            return;
        }
        camera.viewBounds(view);
        camera.prepareScreen();
        for (int i = 0; i < store.size; i++) {
            EntityImpl entity = store.owners[i];
            WorldText text = (WorldText) store.items[i];
            if (!entity.visible || entity.removed || !text.isAttached()) {
                continue;
            }
            float wx = entity.renderX(alpha) + text.offset().x();
            float wy =
                    entity.renderY(alpha) - entity.height / 2f + text.offset().y();
            if (wx < view[0] - 2f || wx > view[2] + 2f || wy < view[1] - 2f || wy > view[3] + 2f) {
                continue;
            }
            float sx = camera.screenX(wx, wy);
            float sy = camera.screenY(wx, wy);
            draw.text(text.text(), new Rect(sx - 300f, sy - 200f, 600f, 200f), text.style(), TextAlign.BOTTOM);
        }
    }
}
