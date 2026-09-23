package dev.gulp.backend.headless;

import dev.gulp.platform.DecodedAudio;
import dev.gulp.platform.DecodedImage;
import dev.gulp.platform.GlyphBitmap;
import dev.gulp.platform.PlatformCallback;
import dev.gulp.platform.PlatformDecoders;
import dev.gulp.platform.PlatformFontFace;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Consumer;

/**
 * Stub decoders. They do not parse file formats: every image decodes to a 1x1 opaque white pixel, every sound to one
 * frame of silence, and every font to a face with fixed square glyphs. Empty input fails, so tests can exercise error
 * paths.
 *
 * <pre>{@code
 * backend.decoders().decodeImage(bytes, callback); // callback gets a 1x1 image before the next frame
 * }</pre>
 */
public final class HeadlessDecoders implements PlatformDecoders {

    private final Consumer<Runnable> mainQueue;

    HeadlessDecoders(Consumer<Runnable> mainQueue) {
        this.mainQueue = mainQueue;
    }

    @Override
    public void decodeImage(ByteBuffer encoded, PlatformCallback<DecodedImage> callback) {
        boolean empty = !encoded.hasRemaining();
        mainQueue.accept(() -> {
            if (empty) {
                callback.failure(new IllegalArgumentException("Empty image data"));
            } else {
                ByteBuffer pixel = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
                pixel.put((byte) 0xff)
                        .put((byte) 0xff)
                        .put((byte) 0xff)
                        .put((byte) 0xff)
                        .flip();
                callback.success(new DecodedImage(1, 1, pixel));
            }
        });
    }

    @Override
    public void decodeAudio(ByteBuffer encoded, PlatformCallback<DecodedAudio> callback) {
        boolean empty = !encoded.hasRemaining();
        mainQueue.accept(() -> {
            if (empty) {
                callback.failure(new IllegalArgumentException("Empty audio data"));
            } else {
                var samples = ByteBuffer.allocateDirect(2)
                        .order(ByteOrder.nativeOrder())
                        .asShortBuffer();
                callback.success(new DecodedAudio(1, 44_100, samples));
            }
        });
    }

    @Override
    public void openFont(ByteBuffer fontFile, PlatformCallback<PlatformFontFace> callback) {
        boolean empty = !fontFile.hasRemaining();
        mainQueue.accept(() -> {
            if (empty) {
                callback.failure(new IllegalArgumentException("Empty font data"));
            } else {
                callback.success(new StubFontFace());
            }
        });
    }

    /** Monospaced face whose glyphs are filled squares of {@code 0.5 * size}. */
    private static final class StubFontFace implements PlatformFontFace {

        @Override
        public int glyphIndex(int codePoint) {
            return codePoint;
        }

        @Override
        public GlyphBitmap rasterize(int glyphIndex, float sizePixels) {
            int side = Math.max(1, Math.round(sizePixels * 0.5f));
            ByteBuffer coverage = ByteBuffer.allocateDirect(side * side);
            for (int i = 0; i < side * side; i++) {
                coverage.put((byte) 0xff);
            }
            coverage.flip();
            return new GlyphBitmap(side, side, 0f, ascent(sizePixels), sizePixels * 0.6f, coverage);
        }

        @Override
        public float kerning(int leftGlyph, int rightGlyph, float sizePixels) {
            return 0f;
        }

        @Override
        public float ascent(float sizePixels) {
            return sizePixels * 0.8f;
        }

        @Override
        public float descent(float sizePixels) {
            return sizePixels * 0.2f;
        }

        @Override
        public float lineHeight(float sizePixels) {
            return sizePixels * 1.2f;
        }

        @Override
        public void dispose() {}
    }
}
