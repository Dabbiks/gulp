package dev.gulp.api.graphics;

import java.io.ByteArrayOutputStream;

/**
 * An image in memory with straight (not premultiplied) RGBA pixels, rows from top to bottom. Drawing operations
 * overwrite pixels without blending.
 *
 * <pre>{@code
 * Pixmap map = new Pixmap(64, 64);
 * map.fill(Color.BLACK);
 * map.fillCircle(32, 32, 20, Color.YELLOW);
 * map.drawLine(0, 0, 63, 63, Color.RED);
 * Texture texture = graphics().texture(map);
 * byte[] png = map.encodePng();
 * }</pre>
 */
public final class Pixmap {

    private final int width;
    private final int height;
    private final int[] pixels;

    /**
     * Creates a transparent pixmap.
     *
     * @param width width in pixels, positive
     * @param height height in pixels, positive
     * @throws IllegalArgumentException if a size is not positive
     */
    public Pixmap(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Pixmap size must be positive: " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
    }

    /**
     * Creates a pixmap from RGBA bytes.
     *
     * @param width width in pixels
     * @param height height in pixels
     * @param rgba {@code width * height * 4} bytes, straight alpha
     * @return the pixmap
     * @throws IllegalArgumentException if the byte count does not match
     */
    public static Pixmap fromRgba(int width, int height, byte[] rgba) {
        if (rgba.length != width * height * 4) {
            throw new IllegalArgumentException("Expected " + (width * height * 4) + " bytes, got " + rgba.length);
        }
        Pixmap pixmap = new Pixmap(width, height);
        for (int i = 0; i < pixmap.pixels.length; i++) {
            int o = i * 4;
            pixmap.pixels[i] = (rgba[o] & 0xff) << 24
                    | (rgba[o + 1] & 0xff) << 16
                    | (rgba[o + 2] & 0xff) << 8
                    | (rgba[o + 3] & 0xff);
        }
        return pixmap;
    }

    /**
     * Width.
     *
     * @return pixels
     */
    public int width() {
        return width;
    }

    /**
     * Height.
     *
     * @return pixels
     */
    public int height() {
        return height;
    }

    /**
     * Reads a pixel.
     *
     * @param x column
     * @param y row
     * @return {@code 0xRRGGBBAA}, or {@code 0} outside the pixmap
     */
    public int getPixel(int x, int y) {
        return inside(x, y) ? pixels[y * width + x] : 0;
    }

    /**
     * Reads a pixel as a color.
     *
     * @param x column
     * @param y row
     * @return the color, transparent outside the pixmap
     */
    public Color getColor(int x, int y) {
        return Color.rgba(getPixel(x, y));
    }

    /**
     * Writes a pixel; ignored outside the pixmap.
     *
     * @param x column
     * @param y row
     * @param rgba {@code 0xRRGGBBAA}
     */
    public void setPixel(int x, int y, int rgba) {
        if (inside(x, y)) {
            pixels[y * width + x] = rgba;
        }
    }

    /**
     * Writes a pixel; ignored outside the pixmap.
     *
     * @param x column
     * @param y row
     * @param color the color
     */
    public void setPixel(int x, int y, Color color) {
        setPixel(x, y, color.toRgba8888());
    }

    /**
     * Fills the whole pixmap.
     *
     * @param color the color
     */
    public void fill(Color color) {
        java.util.Arrays.fill(pixels, color.toRgba8888());
    }

    /**
     * Fills a rectangle, clipped to the pixmap.
     *
     * @param x left edge
     * @param y top edge
     * @param w width
     * @param h height
     * @param color the color
     */
    public void fillRect(int x, int y, int w, int h, Color color) {
        int rgba = color.toRgba8888();
        int x0 = Math.max(0, x);
        int y0 = Math.max(0, y);
        int x1 = Math.min(width, x + w);
        int y1 = Math.min(height, y + h);
        for (int row = y0; row < y1; row++) {
            for (int col = x0; col < x1; col++) {
                pixels[row * width + col] = rgba;
            }
        }
    }

    /**
     * Draws a one-pixel rectangle outline.
     *
     * @param x left edge
     * @param y top edge
     * @param w width
     * @param h height
     * @param color the color
     */
    public void drawRect(int x, int y, int w, int h, Color color) {
        fillRect(x, y, w, 1, color);
        fillRect(x, y + h - 1, w, 1, color);
        fillRect(x, y, 1, h, color);
        fillRect(x + w - 1, y, 1, h, color);
    }

    /**
     * Draws a one-pixel line (Bresenham).
     *
     * @param x0 start column
     * @param y0 start row
     * @param x1 end column
     * @param y1 end row
     * @param color the color
     */
    public void drawLine(int x0, int y0, int x1, int y1, Color color) {
        int rgba = color.toRgba8888();
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        int x = x0;
        int y = y0;
        while (true) {
            setPixel(x, y, rgba);
            if (x == x1 && y == y1) {
                return;
            }
            int e2 = 2 * error;
            if (e2 >= dy) {
                error += dy;
                x += sx;
            }
            if (e2 <= dx) {
                error += dx;
                y += sy;
            }
        }
    }

    /**
     * Draws a one-pixel circle outline (midpoint algorithm).
     *
     * @param cx center column
     * @param cy center row
     * @param radius radius in pixels
     * @param color the color
     */
    public void drawCircle(int cx, int cy, int radius, Color color) {
        int rgba = color.toRgba8888();
        int x = radius;
        int y = 0;
        int error = 1 - radius;
        while (x >= y) {
            setPixel(cx + x, cy + y, rgba);
            setPixel(cx + y, cy + x, rgba);
            setPixel(cx - y, cy + x, rgba);
            setPixel(cx - x, cy + y, rgba);
            setPixel(cx - x, cy - y, rgba);
            setPixel(cx - y, cy - x, rgba);
            setPixel(cx + y, cy - x, rgba);
            setPixel(cx + x, cy - y, rgba);
            y++;
            if (error < 0) {
                error += 2 * y + 1;
            } else {
                x--;
                error += 2 * (y - x) + 1;
            }
        }
    }

    /**
     * Fills a circle.
     *
     * @param cx center column
     * @param cy center row
     * @param radius radius in pixels
     * @param color the color
     */
    public void fillCircle(int cx, int cy, int radius, Color color) {
        int r2 = radius * radius;
        for (int dy = -radius; dy <= radius; dy++) {
            int span = (int) Math.sqrt(r2 - dy * dy);
            fillRect(cx - span, cy + dy, span * 2 + 1, 1, color);
        }
    }

    /**
     * Copies part of another pixmap into this one, clipped to both.
     *
     * @param source the source
     * @param sx source left edge
     * @param sy source top edge
     * @param w width
     * @param h height
     * @param dx destination left edge
     * @param dy destination top edge
     */
    public void blit(Pixmap source, int sx, int sy, int w, int h, int dx, int dy) {
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                int srcX = sx + col;
                int srcY = sy + row;
                if (source.inside(srcX, srcY)) {
                    setPixel(dx + col, dy + row, source.pixels[srcY * source.width + srcX]);
                }
            }
        }
    }

    /**
     * Returns a resized copy using nearest-neighbour sampling.
     *
     * @param newWidth width, positive
     * @param newHeight height, positive
     * @return the scaled pixmap
     */
    public Pixmap scaled(int newWidth, int newHeight) {
        Pixmap result = new Pixmap(newWidth, newHeight);
        for (int y = 0; y < newHeight; y++) {
            int srcY = y * height / newHeight;
            for (int x = 0; x < newWidth; x++) {
                result.pixels[y * newWidth + x] = pixels[srcY * width + x * width / newWidth];
            }
        }
        return result;
    }

    /**
     * Returns a copy flipped upside down.
     *
     * @return the flipped pixmap
     */
    public Pixmap flippedVertically() {
        Pixmap result = new Pixmap(width, height);
        for (int y = 0; y < height; y++) {
            System.arraycopy(pixels, y * width, result.pixels, (height - 1 - y) * width, width);
        }
        return result;
    }

    /**
     * Copies the pixels as RGBA bytes.
     *
     * @return {@code width * height * 4} bytes
     */
    public byte[] toRgba() {
        byte[] out = new byte[pixels.length * 4];
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            out[i * 4] = (byte) (p >>> 24);
            out[i * 4 + 1] = (byte) (p >>> 16);
            out[i * 4 + 2] = (byte) (p >>> 8);
            out[i * 4 + 3] = (byte) p;
        }
        return out;
    }

    /**
     * Encodes the pixmap as an uncompressed-deflate PNG (valid everywhere, larger than a compressed one).
     *
     * @return the PNG file bytes
     */
    public byte[] encodePng() {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        byte[] rgba = toRgba();
        for (int y = 0; y < height; y++) {
            raw.write(0); // filter: none
            raw.write(rgba, y * width * 4, width * 4);
        }
        byte[] data = raw.toByteArray();
        ByteArrayOutputStream zlib = new ByteArrayOutputStream();
        zlib.write(0x78);
        zlib.write(0x01);
        int offset = 0;
        do {
            int length = Math.min(65535, data.length - offset);
            boolean last = offset + length >= data.length;
            zlib.write(last ? 1 : 0);
            zlib.write(length & 0xff);
            zlib.write(length >>> 8);
            zlib.write(~length & 0xff);
            zlib.write((~length >>> 8) & 0xff);
            zlib.write(data, offset, length);
            offset += length;
        } while (offset < data.length);
        writeInt(zlib, adler32(data));

        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}, 0, 8);
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        writeInt(header, width);
        writeInt(header, height);
        header.write(8); // bit depth
        header.write(6); // RGBA
        header.write(0);
        header.write(0);
        header.write(0);
        chunk(png, "IHDR", header.toByteArray());
        chunk(png, "IDAT", zlib.toByteArray());
        chunk(png, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        writeInt(out, data.length);
        byte[] typeBytes = {(byte) type.charAt(0), (byte) type.charAt(1), (byte) type.charAt(2), (byte) type.charAt(3)};
        out.write(typeBytes, 0, 4);
        out.write(data, 0, data.length);
        int crc = 0xffffffff;
        crc = crc32(crc, typeBytes);
        crc = crc32(crc, data);
        writeInt(out, ~crc);
    }

    private static int crc32(int crc, byte[] data) {
        for (byte b : data) {
            crc ^= b & 0xff;
            for (int k = 0; k < 8; k++) {
                crc = (crc & 1) != 0 ? (crc >>> 1) ^ 0xEDB88320 : crc >>> 1;
            }
        }
        return crc;
    }

    private static int adler32(byte[] data) {
        int a = 1;
        int b = 0;
        for (byte value : data) {
            a = (a + (value & 0xff)) % 65521;
            b = (b + a) % 65521;
        }
        return (b << 16) | a;
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value >>> 24);
        out.write(value >>> 16);
        out.write(value >>> 8);
        out.write(value);
    }

    private boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }
}
