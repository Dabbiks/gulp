package dev.gulp.backend.headless;

import dev.gulp.core.audio.WavDecoder;
import dev.gulp.platform.DecodedAudio;
import dev.gulp.platform.DecodedImage;
import dev.gulp.platform.GlyphBitmap;
import dev.gulp.platform.PlatformAudioStream;
import dev.gulp.platform.PlatformCallback;
import dev.gulp.platform.PlatformDecoders;
import dev.gulp.platform.PlatformFontFace;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.function.Consumer;

/**
 * Stub decoders. Images decode to a 1x1 opaque white pixel and fonts to a face with fixed square glyphs. WAV files are
 * decoded for real; other sounds decode to one frame of silence, and other music to one second of silence. Empty input
 * fails, so tests can exercise error paths.
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
            } else if (WavDecoder.isWav(encoded)) {
                try {
                    callback.success(WavDecoder.decode(encoded));
                } catch (IllegalArgumentException error) {
                    callback.failure(error);
                }
            } else {
                var samples = ByteBuffer.allocateDirect(2)
                        .order(ByteOrder.nativeOrder())
                        .asShortBuffer();
                callback.success(new DecodedAudio(1, 44_100, samples));
            }
        });
    }

    @Override
    public void openAudioStream(ByteBuffer encoded, PlatformCallback<PlatformAudioStream> callback) {
        boolean empty = !encoded.hasRemaining();
        mainQueue.accept(() -> {
            if (empty) {
                callback.failure(new IllegalArgumentException("Empty audio data"));
            } else if (WavDecoder.isWav(encoded)) {
                try {
                    callback.success(WavDecoder.open(encoded));
                } catch (IllegalArgumentException error) {
                    callback.failure(error);
                }
            } else {
                callback.success(new SilentStream());
            }
        });
    }

    /** One second of mono silence at 44.1 kHz. */
    private static final class SilentStream implements PlatformAudioStream {
        private static final int FRAMES = 44_100;
        private long frame;

        @Override
        public int channels() {
            return 1;
        }

        @Override
        public int sampleRate() {
            return FRAMES;
        }

        @Override
        public long frames() {
            return FRAMES;
        }

        @Override
        public int read(ShortBuffer out) {
            int count = (int) Math.min(out.remaining(), FRAMES - frame);
            for (int i = 0; i < count; i++) {
                out.put((short) 0);
            }
            frame += count;
            return count;
        }

        @Override
        public void seek(long target) {
            frame = Math.max(0, Math.min(FRAMES, target));
        }

        @Override
        public void close() {}
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
