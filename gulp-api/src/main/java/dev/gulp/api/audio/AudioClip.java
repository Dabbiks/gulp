package dev.gulp.api.audio;

/**
 * A short sound decoded into memory (OGG Vorbis or WAV). Loaded as an asset and played through {@link Sound} or
 * {@link Audio#play(AudioClip, String)}.
 *
 * <pre>{@code
 * AudioClip beep = assets().get(AssetKey.audio("coins:sounds/beep"));
 * audio().play(beep, Audio.UI);
 * }</pre>
 */
public interface AudioClip {

    /**
     * Returns the length.
     *
     * @return seconds
     */
    float duration();

    /**
     * Returns the length in sample frames (one frame holds a sample of every channel).
     *
     * @return frames
     */
    long frames();

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
}
