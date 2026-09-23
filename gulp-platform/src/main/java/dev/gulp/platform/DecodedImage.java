package dev.gulp.platform;

import java.nio.ByteBuffer;

/**
 * An image decoded to 8-bit RGBA, rows from top to bottom, not premultiplied.
 *
 * <pre>{@code
 * gl.texImage2D(Gl.TEXTURE_2D, 0, Gl.RGBA8, image.width(), image.height(), Gl.RGBA, Gl.UNSIGNED_BYTE, image.pixels());
 * }</pre>
 *
 * @param width width in pixels
 * @param height height in pixels
 * @param pixels {@code width * height * 4} bytes, direct and in native order
 */
public record DecodedImage(int width, int height, ByteBuffer pixels) {}
