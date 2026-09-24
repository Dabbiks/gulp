package dev.gulp.api.audio;

/**
 * Procedural sound: the engine asks for samples a little ahead of playback, on the main thread, once per frame.
 *
 * <pre>{@code
 * Playback tone = audio().stream(new PcmSource() {
 *     double phase;
 *     public int channels() { return 1; }
 *     public int sampleRate() { return 44_100; }
 *     public int read(float[] samples, int frames) {
 *         for (int i = 0; i < frames; i++) {
 *             samples[i] = (float) Math.sin(phase) * 0.2f;
 *             phase += 2 * Math.PI * 440 / 44_100;
 *         }
 *         return frames;
 *     }
 * }, Audio.SFX);
 * }</pre>
 */
public interface PcmSource {

    /**
     * Returns the number of channels.
     *
     * @return 1 or 2
     */
    int channels();

    /**
     * Returns the sample rate.
     *
     * @return frames per second, for example {@code 44_100}
     */
    int sampleRate();

    /**
     * Fills the buffer with the next samples, interleaved when stereo.
     *
     * @param samples the buffer, at least {@code frames * channels()} long; values {@code -1..1}
     * @param frames how many frames are wanted
     * @return the number of frames written, less at the end, {@code -1} when the sound is over
     */
    int read(float[] samples, int frames);
}
