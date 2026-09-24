package dev.gulp.backend.desktop;

import dev.gulp.core.MainQueue;
import dev.gulp.core.audio.WavDecoder;
import dev.gulp.platform.DecodedAudio;
import dev.gulp.platform.DecodedImage;
import dev.gulp.platform.PlatformAudioStream;
import dev.gulp.platform.PlatformCallback;
import dev.gulp.platform.PlatformDecoders;
import dev.gulp.platform.PlatformExecutor;
import dev.gulp.platform.PlatformFontFace;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

/**
 * Image and sound decoding (stb_image, stb_vorbis, WAV) on the executor, font opening with FreeType and music streams
 * on the main thread; results arrive on the main thread.
 */
final class DesktopDecoders implements PlatformDecoders {

    private final PlatformExecutor executor;
    private final MainQueue mainQueue;

    DesktopDecoders(PlatformExecutor executor, MainQueue mainQueue) {
        this.executor = executor;
        this.mainQueue = mainQueue;
    }

    @Override
    public void decodeImage(ByteBuffer encoded, PlatformCallback<DecodedImage> callback) {
        // Copy first: the caller may reuse its buffer once this method returns.
        ByteBuffer copy = ByteBuffer.allocateDirect(encoded.remaining()).order(ByteOrder.nativeOrder());
        copy.put(encoded.duplicate()).flip();
        executor.execute(() -> {
            DecodedImage image;
            try {
                image = decode(copy);
            } catch (Throwable error) {
                mainQueue.post(() -> callback.failure(error));
                return;
            }
            mainQueue.post(() -> callback.success(image));
        });
    }

    static DecodedImage decode(ByteBuffer encoded) {
        if (!encoded.hasRemaining()) {
            throw new IllegalArgumentException("Empty image data");
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(encoded, width, height, channels, 4);
            if (pixels == null) {
                throw new IllegalArgumentException("Cannot decode image: " + STBImage.stbi_failure_reason());
            }
            try {
                ByteBuffer owned = ByteBuffer.allocateDirect(pixels.remaining()).order(ByteOrder.nativeOrder());
                // Copy through a duplicate: stbi_image_free frees the address at the buffer position.
                owned.put(pixels.duplicate()).flip();
                return new DecodedImage(width.get(0), height.get(0), owned);
            } finally {
                STBImage.stbi_image_free(pixels);
            }
        }
    }

    @Override
    public void decodeAudio(ByteBuffer encoded, PlatformCallback<DecodedAudio> callback) {
        ByteBuffer copy = copy(encoded);
        executor.execute(() -> {
            DecodedAudio audio;
            try {
                audio = WavDecoder.isWav(copy) ? WavDecoder.decode(copy) : VorbisStream.decodeAll(copy);
            } catch (Throwable error) {
                mainQueue.post(() -> callback.failure(error));
                return;
            }
            mainQueue.post(() -> callback.success(audio));
        });
    }

    @Override
    public void openAudioStream(ByteBuffer encoded, PlatformCallback<PlatformAudioStream> callback) {
        ByteBuffer copy = copy(encoded);
        mainQueue.post(() -> {
            PlatformAudioStream stream;
            try {
                stream = WavDecoder.isWav(copy) ? WavDecoder.open(copy) : VorbisStream.open(copy);
            } catch (RuntimeException error) {
                callback.failure(error);
                return;
            }
            callback.success(stream);
        });
    }

    private static ByteBuffer copy(ByteBuffer data) {
        ByteBuffer copy = ByteBuffer.allocateDirect(data.remaining()).order(ByteOrder.nativeOrder());
        copy.put(data.duplicate()).flip();
        return copy;
    }

    @Override
    public void openFont(ByteBuffer fontFile, PlatformCallback<PlatformFontFace> callback) {
        // FreeType faces are used from the main thread only, so they are opened there too.
        ByteBuffer copy = ByteBuffer.allocateDirect(fontFile.remaining()).order(ByteOrder.nativeOrder());
        copy.put(fontFile.duplicate()).flip();
        mainQueue.post(() -> {
            PlatformFontFace face;
            try {
                face = FreeTypeFontFace.open(copy);
            } catch (RuntimeException e) {
                callback.failure(e);
                return;
            }
            callback.success(face);
        });
    }
}
