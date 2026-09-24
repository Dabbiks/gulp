package dev.gulp.platform;

import java.nio.ShortBuffer;

/**
 * Audio decoded piece by piece, for music. Used only on the main thread.
 *
 * <pre>{@code
 * int frames = stream.read(chunk);          // 16-bit interleaved samples
 * if (frames == 0) stream.seek(loopStart);
 * }</pre>
 */
public interface PlatformAudioStream {

    /**
     * Returns the number of channels.
     *
     * @return 1 or 2
     */
    int channels();

    /**
     * Returns the sample rate.
     *
     * @return frames per second
     */
    int sampleRate();

    /**
     * Returns the length.
     *
     * @return sample frames
     */
    long frames();

    /**
     * Decodes the next samples into the buffer, from its position up to its limit, and advances the position.
     *
     * @param out the buffer, interleaved
     * @return the number of frames written, {@code 0} at the end
     */
    int read(ShortBuffer out);

    /**
     * Moves to a frame.
     *
     * @param frame the frame, from 0
     */
    void seek(long frame);

    /** Releases the decoder. */
    void close();
}
