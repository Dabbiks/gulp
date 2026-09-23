package dev.gulp.core.asset;

import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.asset.AssetLoadContext;
import dev.gulp.api.asset.AssetLoader;
import dev.gulp.api.asset.AssetType;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.graphics.TextureAtlas;
import dev.gulp.api.graphics.TextureFilter;
import dev.gulp.api.scheduler.Promise;
import dev.gulp.api.text.Font;
import dev.gulp.core.data.JsonReader;
import dev.gulp.core.graphics.GraphicsImpl;
import dev.gulp.core.scheduler.PromiseImpl;
import dev.gulp.core.text.BitmapFontImpl;
import dev.gulp.core.text.DynamicFontImpl;
import dev.gulp.core.text.FontImpl;
import dev.gulp.core.text.MsdfFontImpl;
import dev.gulp.platform.PlatformCallback;
import dev.gulp.platform.PlatformDecoders;
import dev.gulp.platform.PlatformFontFace;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Loaders of the engine's own asset types: fonts, atlases and atlas regions. */
final class BuiltinLoaders {

    /** Largest page side of atlases packed at load time. */
    private static final int DEVELOPMENT_PAGE_SIZE = 2048;

    private BuiltinLoaders() {}

    static void register(AssetsImpl assets, GraphicsImpl graphics, PlatformDecoders decoders) {
        assets.registerLoader(AssetType.FONT, new AssetLoader<>() {
            @Override
            public Promise<Font> load(AssetLoadContext context) {
                return loadFont(assets, graphics, decoders, context);
            }

            @Override
            public void dispose(Font asset) {
                if (asset instanceof FontImpl font) {
                    font.dispose();
                }
            }
        });
        assets.registerLoader(AssetType.ATLAS, new AssetLoader<>() {
            @Override
            public Promise<TextureAtlas> load(AssetLoadContext context) {
                return loadAtlas(assets, graphics, context);
            }

            @Override
            public void dispose(TextureAtlas asset) {
                if (asset instanceof TextureAtlasImpl atlas) {
                    atlas.dispose();
                }
            }
        });
        assets.registerLoader(AssetType.REGION, context -> {
            String namespace = context.key().key().namespace();
            String[] parts =
                    TextureAtlasImpl.splitRegion(namespace, context.key().key().path());
            return context.dependency(AssetKey.atlas(namespace + ":" + parts[0]))
                    .map(atlas -> atlas.region(parts[1]));
        });
    }

    /** Key of a file next to another one: {@code gulp/fonts/default.msdf.json} + {@code default_0.png}. */
    static String sibling(String path, String file) {
        int slash = path.indexOf('/');
        String namespace = path.substring(0, slash);
        String folder = path.substring(slash + 1, path.lastIndexOf('/') + 1);
        return namespace + ":" + folder + file;
    }

    private static Promise<Font> loadFont(
            AssetsImpl assets, GraphicsImpl graphics, PlatformDecoders decoders, AssetLoadContext context) {
        String path = context.path();
        String name = context.key().key().toString();
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".msdf.json")) {
            return context.text().flatMap(text -> {
                JsonObject json = JsonReader.parse(text).asObject();
                List<Promise<Texture>> pages = new ArrayList<>();
                for (String page : MsdfFontImpl.pages(json)) {
                    pages.add(context.dependency(AssetKey.texture(sibling(path, page))));
                }
                return assets.all(pages).map(list -> new MsdfFontImpl(name, json, list));
            });
        }
        if (lower.endsWith(".fnt")) {
            return context.text().flatMap(text -> {
                BitmapFontImpl.Description description = BitmapFontImpl.parse(text);
                List<Promise<Texture>> pages = new ArrayList<>();
                for (String page : description.pages()) {
                    pages.add(context.dependency(AssetKey.texture(sibling(path, page))));
                }
                return assets.all(pages).map(list -> {
                    if (!description.smooth()) {
                        for (Texture page : list) {
                            page.setFilter(TextureFilter.NEAREST);
                        }
                    }
                    return BitmapFontImpl.create(name, description, list);
                });
            });
        }
        return context.bytes().flatMap(bytes -> {
            PromiseImpl<PlatformFontFace> face = assets.newPromise();
            ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
            buffer.put(bytes).flip();
            decoders.openFont(buffer, new PlatformCallback<>() {
                @Override
                public void success(PlatformFontFace value) {
                    face.complete(value);
                }

                @Override
                public void failure(Throwable error) {
                    face.fail(error);
                }
            });
            return face.map(opened -> (Font) new DynamicFontImpl(name, opened, graphics::texture));
        });
    }

    private static Promise<TextureAtlas> loadAtlas(AssetsImpl assets, GraphicsImpl graphics, AssetLoadContext context) {
        String path = context.path();
        if (path.endsWith("/")) {
            // Development: no packed atlas yet, so pack the folder's images now.
            List<String> images = new ArrayList<>();
            for (String file : assets.filesUnder(path)) {
                String lower = file.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".png")
                        || lower.endsWith(".jpg")
                        || lower.endsWith(".jpeg")
                        || lower.endsWith(".webp")) {
                    images.add(file);
                }
            }
            List<Promise<Pixmap>> decoded = new ArrayList<>();
            for (String image : images) {
                decoded.add(assets.readBytes(image).flatMap(graphics::decode));
            }
            return assets.all(decoded).map(pixmaps -> {
                List<AtlasBuilder.Input> inputs = new ArrayList<>();
                for (int i = 0; i < images.size(); i++) {
                    String relative = images.get(i).substring(path.length());
                    inputs.add(
                            new AtlasBuilder.Input(relative.substring(0, relative.lastIndexOf('.')), pixmaps.get(i)));
                }
                AtlasBuilder.Result packed = AtlasBuilder.build(inputs, DEVELOPMENT_PAGE_SIZE, 2);
                List<Texture> pages = new ArrayList<>();
                for (Pixmap page : packed.pages()) {
                    pages.add(graphics.texture(page));
                }
                return new TextureAtlasImpl(TextureAtlasImpl.fromFrames(packed.frames(), pages), pages, pages);
            });
        }
        return context.text().flatMap(text -> {
            JsonObject json = JsonReader.parse(text).asObject();
            List<Promise<Texture>> pages = new ArrayList<>();
            for (String page : TextureAtlasImpl.pageFiles(json)) {
                pages.add(context.dependency(AssetKey.texture(sibling(path, page))));
            }
            return assets.all(pages).map(list ->
                    (TextureAtlas) new TextureAtlasImpl(TextureAtlasImpl.parse(json, list), list, List.of()));
        });
    }
}
