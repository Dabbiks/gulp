package dev.gulp.core.asset;

import dev.gulp.api.data.JsonArray;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.core.graphics.RectPacker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Packs images into atlas pages: transparent borders are trimmed, edges are extruded by one pixel so linear filtering
 * does not bleed neighbours in, and images are packed tallest first. Shared by the {@code gulp-tools} packer and the
 * development-mode packing at startup.
 *
 * <pre>{@code
 * AtlasBuilder.Result atlas = AtlasBuilder.build(images, 2048, 2);
 * String json = JsonWriter.write(atlas.json("sprites"), false);
 * }</pre>
 */
public final class AtlasBuilder {

    /** Pixels repeated around each image against texture bleeding. */
    public static final int EXTRUDE = 1;

    private AtlasBuilder() {}

    /**
     * An image to pack.
     *
     * @param name region name, the path without extension, for example {@code player/idle_0}
     * @param image the pixels
     */
    public record Input(String name, Pixmap image) {}

    /**
     * A packed region.
     *
     * @param name region name
     * @param page page index
     * @param x left edge of the trimmed image on the page
     * @param y top edge of the trimmed image on the page
     * @param width trimmed width
     * @param height trimmed height
     * @param offsetX pixels trimmed on the left
     * @param offsetY pixels trimmed at the top
     * @param originalWidth width before trimming
     * @param originalHeight height before trimming
     */
    public record Frame(
            String name,
            int page,
            int x,
            int y,
            int width,
            int height,
            int offsetX,
            int offsetY,
            int originalWidth,
            int originalHeight) {}

    /**
     * Packed pages and frames.
     *
     * @param pages page images
     * @param frames packed regions in input order
     */
    public record Result(List<Pixmap> pages, List<Frame> frames) {

        /**
         * Describes the atlas in the multi-page TexturePacker JSON format that the engine reads.
         *
         * @param baseName page files are {@code baseName_<page>.png}
         * @return the JSON
         */
        public JsonObject json(String baseName) {
            JsonArray.Builder textures = JsonArray.builder();
            for (int page = 0; page < pages.size(); page++) {
                JsonArray.Builder frameArray = JsonArray.builder();
                for (Frame frame : frames) {
                    if (frame.page() != page) {
                        continue;
                    }
                    boolean trimmed =
                            frame.width() != frame.originalWidth() || frame.height() != frame.originalHeight();
                    frameArray.add(JsonObject.builder()
                            .put("filename", frame.name())
                            .put("frame", rect(frame.x(), frame.y(), frame.width(), frame.height()))
                            .put("rotated", false)
                            .put("trimmed", trimmed)
                            .put(
                                    "spriteSourceSize",
                                    rect(frame.offsetX(), frame.offsetY(), frame.width(), frame.height()))
                            .put(
                                    "sourceSize",
                                    JsonObject.builder()
                                            .put("w", frame.originalWidth())
                                            .put("h", frame.originalHeight())
                                            .build())
                            .build());
                }
                Pixmap image = pages.get(page);
                textures.add(JsonObject.builder()
                        .put("image", baseName + "_" + page + ".png")
                        .put(
                                "size",
                                JsonObject.builder()
                                        .put("w", image.width())
                                        .put("h", image.height())
                                        .build())
                        .put("frames", frameArray.build())
                        .build());
            }
            return JsonObject.builder().put("textures", textures.build()).build();
        }

        private static JsonObject rect(int x, int y, int w, int h) {
            return JsonObject.builder()
                    .put("x", x)
                    .put("y", y)
                    .put("w", w)
                    .put("h", h)
                    .build();
        }
    }

    /**
     * Packs images.
     *
     * @param inputs the images
     * @param maxPageSize largest page side, for example 2048 (at most 4096)
     * @param padding empty pixels between images, besides the extruded edge
     * @return the pages and frames
     */
    public static Result build(List<Input> inputs, int maxPageSize, int padding) {
        int[][] trims = new int[inputs.size()][];
        long area = 0;
        int largest = 1;
        for (int i = 0; i < inputs.size(); i++) {
            int[] trim = trim(inputs.get(i).image());
            trims[i] = trim;
            int w = trim[2] + 2 * (EXTRUDE + padding);
            int h = trim[3] + 2 * (EXTRUDE + padding);
            area += (long) w * h;
            largest = Math.max(largest, Math.max(w, h));
        }
        int side = 32;
        while (side < maxPageSize && ((long) side * side < area * 1.1 || side < largest)) {
            side *= 2;
        }
        if (largest > side) {
            throw new IllegalArgumentException("An image of " + largest + " pixels does not fit pages of " + side);
        }
        RectPacker packer = new RectPacker(side, side, padding + EXTRUDE);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.<Integer>comparingInt(i -> trims[i][3]).reversed());
        Frame[] frames = new Frame[inputs.size()];
        for (int i : order) {
            int[] trim = trims[i];
            RectPacker.Placement at = packer.add(trim[2], trim[3]);
            Pixmap image = inputs.get(i).image();
            frames[i] = new Frame(
                    inputs.get(i).name(),
                    at.page(),
                    at.x(),
                    at.y(),
                    trim[2],
                    trim[3],
                    trim[0],
                    trim[1],
                    image.width(),
                    image.height());
        }
        List<Pixmap> pages = new ArrayList<>();
        for (int page = 0; page < Math.max(1, packer.pageCount()); page++) {
            int height = side;
            if (packer.pageCount() > 0 && page == packer.pageCount() - 1) {
                int used = packer.usedSize(page)[1];
                while (height / 2 >= used && height > 32) {
                    height /= 2;
                }
            }
            pages.add(new Pixmap(side, height));
        }
        for (int i = 0; i < inputs.size(); i++) {
            copy(inputs.get(i).image(), frames[i], pages.get(frames[i].page()));
        }
        return new Result(pages, List.of(frames));
    }

    /** Opaque bounds as {@code {x, y, width, height}}; fully transparent images keep one pixel. */
    static int[] trim(Pixmap image) {
        int minX = image.width();
        int minY = image.height();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                if ((image.getPixel(x, y) & 0xff) != 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) {
            return new int[] {0, 0, 1, 1};
        }
        return new int[] {minX, minY, maxX - minX + 1, maxY - minY + 1};
    }

    private static void copy(Pixmap source, Frame frame, Pixmap page) {
        for (int y = -EXTRUDE; y < frame.height() + EXTRUDE; y++) {
            int sy = frame.offsetY() + Math.max(0, Math.min(frame.height() - 1, y));
            for (int x = -EXTRUDE; x < frame.width() + EXTRUDE; x++) {
                int sx = frame.offsetX() + Math.max(0, Math.min(frame.width() - 1, x));
                page.setPixel(frame.x() + x, frame.y() + y, source.getPixel(sx, sy));
            }
        }
    }
}
