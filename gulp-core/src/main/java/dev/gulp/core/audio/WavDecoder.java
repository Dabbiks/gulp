package dev.gulp.core.audio;

import dev.gulp.platform.DecodedAudio;
import dev.gulp.platform.PlatformAudioStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * Reads RIFF WAVE files: PCM with 8, 16, 24 or 32 bits and 32-bit float, mono or stereo. Shared by the backends that
 * decode WAV themselves.
 *
 * <pre>{@code
 * DecodedAudio pcm = WavDecoder.decode(bytes);
 * PlatformAudioStream stream = WavDecoder.open(bytes);
 * }</pre>
 */
public final class WavDecoder {

    private static final int PCM = 1;
    private static final int FLOAT = 3;
    private static final int EXTENSIBLE = 0xFFFE;

    private WavDecoder() {}

    /**
     * Returns whether data starts like a WAVE file.
     *
     * @param data the file contents
     * @return {@code true} for RIFF WAVE
     */
    public static boolean isWav(ByteBuffer data) {
        int start = data.position();
        return data.remaining() >= 12
                && data.get(start) == 'R'
                && data.get(start + 1) == 'I'
                && data.get(start + 2) == 'F'
                && data.get(start + 3) == 'F'
                && data.get(start + 8) == 'W'
                && data.get(start + 9) == 'A'
                && data.get(start + 10) == 'V'
                && data.get(start + 11) == 'E';
    }

    /**
     * Decodes a whole file to 16-bit PCM.
     *
     * @param data the file contents
     * @return the samples
     * @throws IllegalArgumentException if the file is not a supported WAVE file
     */
    public static DecodedAudio decode(ByteBuffer data) {
        Stream stream = open(data);
        ShortBuffer samples = ShortBuffer.allocate((int) (stream.frames() * stream.channels()));
        while (samples.hasRemaining() && stream.read(samples) > 0) {
            // reads until the end
        }
        samples.flip();
        return new DecodedAudio(stream.channels(), stream.sampleRate(), samples);
    }

    /**
     * Opens a file for reading in pieces.
     *
     * @param data the file contents
     * @return the stream
     * @throws IllegalArgumentException if the file is not a supported WAVE file
     */
    public static Stream open(ByteBuffer data) {
        ByteBuffer wav = data.slice().order(ByteOrder.LITTLE_ENDIAN);
        if (!isWav(wav)) {
            throw new IllegalArgumentException("Not a WAVE file");
        }
        int format = -1;
        int channels = 0;
        int sampleRate = 0;
        int bits = 0;
        int dataStart = -1;
        int dataLength = 0;
        int position = 12;
        while (position + 8 <= wav.limit()) {
            int id = wav.getInt(position);
            int size = wav.getInt(position + 4);
            int body = position + 8;
            if (id == 0x20746d66) { // "fmt "
                format = wav.getShort(body) & 0xFFFF;
                channels = wav.getShort(body + 2);
                sampleRate = wav.getInt(body + 4);
                bits = wav.getShort(body + 14);
                if (format == EXTENSIBLE && size >= 26) {
                    format = wav.getShort(body + 24) & 0xFFFF;
                }
            } else if (id == 0x61746164) { // "data"
                dataStart = body;
                dataLength = Math.min(size < 0 ? Integer.MAX_VALUE : size, wav.limit() - body);
                break;
            }
            position = body + size + (size & 1);
        }
        boolean supported = (format == PCM && (bits == 8 || bits == 16 || bits == 24 || bits == 32))
                || (format == FLOAT && bits == 32);
        if (dataStart < 0 || !supported || channels < 1 || channels > 2 || sampleRate <= 0) {
            throw new IllegalArgumentException(
                    "Unsupported WAVE file: format " + format + ", " + bits + " bits, " + channels + " channels");
        }
        return new Stream(wav, dataStart, dataLength, channels, sampleRate, bits, format == FLOAT);
    }

    /**
     * A WAVE file read in pieces.
     *
     * <pre>{@code
     * Stream stream = WavDecoder.open(bytes);
     * stream.read(chunk);
     * }</pre>
     */
    public static final class Stream implements PlatformAudioStream {
        private final ByteBuffer wav;
        private final int dataStart;
        private final int channels;
        private final int sampleRate;
        private final int bytesPerSample;
        private final boolean floating;
        private final long frames;
        private long frame;

        private Stream(
                ByteBuffer wav,
                int dataStart,
                int dataLength,
                int channels,
                int sampleRate,
                int bits,
                boolean floating) {
            this.wav = wav;
            this.dataStart = dataStart;
            this.channels = channels;
            this.sampleRate = sampleRate;
            this.bytesPerSample = bits / 8;
            this.floating = floating;
            this.frames = dataLength / (long) (bytesPerSample * channels);
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
            int wanted = (int) Math.min(out.remaining() / channels, frames - frame);
            int offset = (int) (dataStart + frame * bytesPerSample * channels);
            for (int i = 0; i < wanted * channels; i++) {
                out.put(sample(offset));
                offset += bytesPerSample;
            }
            frame += wanted;
            return wanted;
        }

        private short sample(int offset) {
            switch (bytesPerSample) {
                case 1 -> {
                    return (short) (((wav.get(offset) & 0xFF) - 128) << 8);
                }
                case 2 -> {
                    return wav.getShort(offset);
                }
                case 3 -> {
                    return (short) ((wav.get(offset + 1) & 0xFF) | (wav.get(offset + 2) << 8));
                }
                default -> {
                    if (floating) {
                        float value = Math.max(-1f, Math.min(1f, wav.getFloat(offset)));
                        return (short) (value * 32767f);
                    }
                    return (short) (wav.getInt(offset) >> 16);
                }
            }
        }

        @Override
        public void seek(long target) {
            frame = Math.max(0L, Math.min(frames, target));
        }

        @Override
        public void close() {
            // nothing to release
        }
    }
}
