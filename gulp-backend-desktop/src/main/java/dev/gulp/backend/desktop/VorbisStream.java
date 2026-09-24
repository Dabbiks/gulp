package dev.gulp.backend.desktop;

import static org.lwjgl.stb.STBVorbis.stb_vorbis_close;
import static org.lwjgl.stb.STBVorbis.stb_vorbis_get_info;
import static org.lwjgl.stb.STBVorbis.stb_vorbis_get_samples_short_interleaved;
import static org.lwjgl.stb.STBVorbis.stb_vorbis_open_memory;
import static org.lwjgl.stb.STBVorbis.stb_vorbis_seek;
import static org.lwjgl.stb.STBVorbis.stb_vorbis_stream_length_in_samples;
import static org.lwjgl.system.MemoryUtil.NULL;

import dev.gulp.platform.PlatformAudioStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** OGG Vorbis decoded piece by piece with stb_vorbis; more than two channels are mixed down to stereo. */
final class VorbisStream implements PlatformAudioStream {

    private final ByteBuffer encoded;
    private final long handle;
    private final int channels;
    private final int sampleRate;
    private final long frames;
    private ShortBuffer scratch;

    private VorbisStream(ByteBuffer encoded, long handle, int channels, int sampleRate, long frames) {
        this.encoded = encoded;
        this.handle = handle;
        this.channels = channels;
        this.sampleRate = sampleRate;
        this.frames = frames;
        this.scratch = MemoryUtil.memAllocShort(4096 * channels);
    }

    /**
     * Opens a file.
     *
     * @param encoded the file contents in a direct buffer, kept until {@link #close()}
     * @return the stream
     * @throws IllegalArgumentException if the data is not OGG Vorbis
     */
    static VorbisStream open(ByteBuffer encoded) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer error = stack.mallocInt(1);
            long handle = stb_vorbis_open_memory(encoded, error, null);
            if (handle == NULL) {
                throw new IllegalArgumentException("Cannot decode OGG Vorbis (stb_vorbis error " + error.get(0) + ")");
            }
            STBVorbisInfo info = STBVorbisInfo.malloc(stack);
            stb_vorbis_get_info(handle, info);
            int channels = Math.min(2, info.channels());
            return new VorbisStream(
                    encoded, handle, channels, info.sample_rate(), stb_vorbis_stream_length_in_samples(handle));
        }
    }

    /**
     * Decodes a whole file.
     *
     * @param encoded the file contents in a direct buffer
     * @return interleaved samples in a direct buffer, with channel count and rate
     */
    static dev.gulp.platform.DecodedAudio decodeAll(ByteBuffer encoded) {
        VorbisStream stream = open(encoded);
        try {
            ShortBuffer owned = ByteBuffer.allocateDirect((int) (stream.frames * stream.channels * 2))
                    .order(java.nio.ByteOrder.nativeOrder())
                    .asShortBuffer();
            while (owned.hasRemaining() && stream.read(owned) > 0) {
                // decodes until the end
            }
            owned.flip();
            return new dev.gulp.platform.DecodedAudio(stream.channels, stream.sampleRate, owned);
        } finally {
            stream.close();
        }
    }

    @Override
    public int channels() {
        return channels;
    }

    @Override
    public int sampleRate() {
        return sampleRate;
    }

    @Override
    public long frames() {
        return frames;
    }

    @Override
    public int read(ShortBuffer out) {
        if (out.isDirect()) {
            int read = stb_vorbis_get_samples_short_interleaved(handle, channels, out);
            out.position(out.position() + read * channels);
            return read;
        }
        if (scratch.capacity() < out.remaining()) {
            MemoryUtil.memFree(scratch);
            scratch = MemoryUtil.memAllocShort(out.remaining());
        }
        scratch.clear().limit(out.remaining());
        int read = stb_vorbis_get_samples_short_interleaved(handle, channels, scratch);
        scratch.limit(read * channels);
        out.put(scratch);
        return read;
    }

    @Override
    public void seek(long frame) {
        stb_vorbis_seek(handle, (int) Math.max(0, Math.min(frames, frame)));
    }

    @Override
    public void close() {
        stb_vorbis_close(handle);
        MemoryUtil.memFree(scratch);
        encoded.clear();
    }
}
