package dev.gulp.core.asset;

import dev.gulp.api.data.JsonObject;
import dev.gulp.api.data.JsonValue;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.graphics.TextureAtlas;
import dev.gulp.api.graphics.TextureRegion;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * {@link TextureAtlas}. Reads the three TexturePacker JSON layouts: multi-page {@code {"textures":[{image, frames:[…]}]}}
 * (written by the Gulp packer), hash {@code {"frames":{name:{…}}, "meta":{"image":…}}} and array
 * {@code {"frames":[{"filename":…}], "meta":…}}. Region names drop the file extension.
 */
public final class TextureAtlasImpl implements TextureAtlas {

    /** Compares names so that {@code run_2} sorts before {@code run_10}. */
    static final Comparator<String> NATURAL = TextureAtlasImpl::compareNatural;

    private Map<String, TextureRegion> regions = new TreeMap<>(NATURAL);
    private List<Texture> pages = List.of();
    private List<Texture> owned = List.of();

    /**
     * Creates an atlas.
     *
     * @param regions regions by name
     * @param pages the page textures
     * @param owned pages this atlas created and must dispose (packed at load time), or empty
     */
    public TextureAtlasImpl(Map<String, TextureRegion> regions, List<Texture> pages, List<Texture> owned) {
        replace(regions, pages, owned);
    }

    /**
     * Swaps in new contents after a reload; the object stays the same.
     *
     * @param newRegions the regions
     * @param newPages the pages
     * @param newOwned pages to dispose with the atlas
     */
    public void replace(Map<String, TextureRegion> newRegions, List<Texture> newPages, List<Texture> newOwned) {
        Map<String, TextureRegion> sorted = new TreeMap<>(NATURAL);
        sorted.putAll(newRegions);
        List<Texture> previous = owned;
        this.regions = sorted;
        this.pages = List.copyOf(newPages);
        this.owned = List.copyOf(newOwned);
        for (Texture page : previous) {
            if (!newOwned.contains(page)) {
                page.dispose();
            }
        }
    }

    /**
     * Returns the pages this atlas created itself.
     *
     * @return the owned pages
     */
    List<Texture> ownedPages() {
        return owned;
    }

    /** Disposes the pages this atlas created. */
    public void dispose() {
        for (Texture page : owned) {
            page.dispose();
        }
    }

    /**
     * Lists the page images a description refers to, in order.
     *
     * @param json the parsed atlas description
     * @return the image file names
     */
    public static List<String> pageFiles(JsonObject json) {
        List<String> files = new ArrayList<>();
        JsonValue textures = json.get("textures");
        if (textures != null) {
            for (JsonValue texture : textures.asArray()) {
                files.add(texture.asObject().getOrThrow("image").asString());
            }
            return files;
        }
        JsonValue meta = json.get("meta");
        if (meta == null || meta.asObject().get("image") == null) {
            throw new IllegalArgumentException("Atlas JSON has neither 'textures' nor 'meta.image'");
        }
        files.add(meta.asObject().getOrThrow("image").asString());
        return files;
    }

    /**
     * Reads the regions of a description.
     *
     * @param json the parsed atlas description
     * @param pages the loaded page textures, in the order of {@link #pageFiles(JsonObject)}
     * @return the regions by name
     */
    public static Map<String, TextureRegion> parse(JsonObject json, List<Texture> pages) {
        Map<String, TextureRegion> result = new LinkedHashMap<>();
        JsonValue textures = json.get("textures");
        if (textures != null) {
            int page = 0;
            for (JsonValue texture : textures.asArray()) {
                for (JsonValue frame : texture.asObject().getOrThrow("frames").asArray()) {
                    JsonObject object = frame.asObject();
                    add(result, object.getOrThrow("filename").asString(), object, pages.get(page));
                }
                page++;
            }
            return result;
        }
        JsonValue frames = json.getOrThrow("frames");
        if (frames instanceof JsonObject hash) {
            for (String name : hash.names()) {
                add(result, name, hash.getOrThrow(name).asObject(), pages.get(0));
            }
        } else {
            for (JsonValue frame : frames.asArray()) {
                JsonObject object = frame.asObject();
                add(result, object.getOrThrow("filename").asString(), object, pages.get(0));
            }
        }
        return result;
    }

    private static void add(Map<String, TextureRegion> result, String file, JsonObject frame, Texture page) {
        JsonObject rect = frame.getOrThrow("frame").asObject();
        int x = rect.getOrThrow("x").asInt();
        int y = rect.getOrThrow("y").asInt();
        int w = rect.getOrThrow("w").asInt();
        int h = rect.getOrThrow("h").asInt();
        JsonValue rotatedValue = frame.get("rotated");
        boolean rotated = rotatedValue != null && rotatedValue.asBoolean();
        int offsetX = 0;
        int offsetY = 0;
        int originalWidth = w;
        int originalHeight = h;
        JsonValue source = frame.get("spriteSourceSize");
        if (source != null) {
            offsetX = source.asObject().getOrThrow("x").asInt();
            offsetY = source.asObject().getOrThrow("y").asInt();
        }
        JsonValue size = frame.get("sourceSize");
        if (size != null) {
            originalWidth = size.asObject().getOrThrow("w").asInt();
            originalHeight = size.asObject().getOrThrow("h").asInt();
        }
        // TexturePacker gives the unrotated size; a rotated region covers h x w pixels of the page.
        TextureRegion region = rotated
                ? new TextureRegion(
                        page, x, y, h, w, false, false, true, offsetX, offsetY, originalWidth, originalHeight)
                : new TextureRegion(
                        page, x, y, w, h, false, false, false, offsetX, offsetY, originalWidth, originalHeight);
        int dot = file.lastIndexOf('.');
        String name = dot > file.lastIndexOf('/') ? file.substring(0, dot) : file;
        result.put(name, region);
    }

    /**
     * Builds regions from packed frames.
     *
     * @param frames the frames
     * @param pages the page textures
     * @return the regions by name
     */
    public static Map<String, TextureRegion> fromFrames(List<AtlasBuilder.Frame> frames, List<Texture> pages) {
        Map<String, TextureRegion> result = new LinkedHashMap<>();
        for (AtlasBuilder.Frame frame : frames) {
            result.put(
                    frame.name(),
                    new TextureRegion(
                            pages.get(frame.page()),
                            frame.x(),
                            frame.y(),
                            frame.width(),
                            frame.height(),
                            false,
                            false,
                            false,
                            frame.offsetX(),
                            frame.offsetY(),
                            frame.originalWidth(),
                            frame.originalHeight()));
        }
        return result;
    }

    @Override
    public TextureRegion region(String name) {
        TextureRegion region = regions.get(name);
        if (region == null) {
            List<String> similar = new ArrayList<>();
            String last = name.substring(name.lastIndexOf('/') + 1);
            for (String candidate : regions.keySet()) {
                if (candidate.contains(last) && similar.size() < 5) {
                    similar.add(candidate);
                }
            }
            throw new IllegalArgumentException("No region '" + name + "' in the atlas"
                    + (similar.isEmpty() ? "" : "; similar: " + String.join(", ", similar)));
        }
        return region;
    }

    @Override
    public @Nullable TextureRegion find(String name) {
        return regions.get(name);
    }

    @Override
    public List<TextureRegion> regions(String prefix) {
        List<TextureRegion> found = new ArrayList<>();
        for (Map.Entry<String, TextureRegion> entry : regions.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                found.add(entry.getValue());
            }
        }
        return found;
    }

    @Override
    public Set<String> names() {
        return Collections.unmodifiableSet(regions.keySet());
    }

    @Override
    public List<Texture> pages() {
        return pages;
    }

    /**
     * Returns the regions, for tests.
     *
     * @return the regions by name
     */
    Map<String, TextureRegion> regionMap() {
        return regions;
    }

    static int compareNatural(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i;
                int sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) {
                    i++;
                }
                while (j < b.length() && Character.isDigit(b.charAt(j))) {
                    j++;
                }
                String na = a.substring(si, i).replaceFirst("^0+(?=.)", "");
                String nb = b.substring(sj, j).replaceFirst("^0+(?=.)", "");
                int compare = na.length() != nb.length() ? Integer.compare(na.length(), nb.length()) : na.compareTo(nb);
                if (compare != 0) {
                    return compare;
                }
                continue;
            }
            if (ca != cb) {
                return Character.compare(ca, cb);
            }
            i++;
            j++;
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    /**
     * Returns the atlas key and region name of a region key such as {@code coins:sprites/player/idle_0}.
     *
     * @param namespace the namespace
     * @param path the path
     * @return {@code {atlasPath, regionName}}
     * @throws IllegalArgumentException if the path has no folder part
     */
    public static String[] splitRegion(String namespace, String path) {
        int slash = path.indexOf('/');
        if (slash <= 0 || slash == path.length() - 1) {
            throw new IllegalArgumentException("Region key '" + namespace + ":" + path
                    + "' needs the atlas folder first, for example " + namespace + ":sprites/" + path);
        }
        return new String[] {path.substring(0, slash), path.substring(slash + 1)};
    }

    static <T> List<T> map(List<String> names, Function<String, T> mapper) {
        List<T> list = new ArrayList<>();
        for (String name : names) {
            list.add(mapper.apply(name));
        }
        return list;
    }
}
