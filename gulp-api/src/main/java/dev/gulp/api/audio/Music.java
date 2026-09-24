package dev.gulp.api.audio;

/**
 * A music track, streamed while it plays instead of being decoded into memory. Loops by default; loop points are in
 * sample frames, so a track can have an intro that plays once.
 *
 * <pre>{@code
 * Music theme = assets().get(GameAssets.Music.THEME);
 * theme.setLoopPoints(88_200, theme.frames()); // skip the two-second intro when looping
 * audio().music().play(theme, 1.5f);
 * }</pre>
 */
public interface Music {

    /**
     * Returns the asset key text, for logs.
     *
     * @return the name
     */
    String name();

    /**
     * Returns the length.
     *
     * @return seconds
     */
    float duration();

    /**
     * Returns the length in sample frames.
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

    /**
     * Returns whether the track loops when played alone.
     *
     * @return {@code true} by default
     */
    boolean isLooping();

    /**
     * Chooses whether the track loops when played alone. Playlists ignore this.
     *
     * @param looping whether to loop
     * @return this track
     */
    Music setLooping(boolean looping);

    /**
     * Returns the frame playback jumps back to.
     *
     * @return the loop start
     */
    long loopStart();

    /**
     * Returns the frame at which playback jumps back.
     *
     * @return the loop end, {@link #frames()} by default
     */
    long loopEnd();

    /**
     * Sets the loop points.
     *
     * @param start the frame to jump back to
     * @param end the frame at which to jump, at most {@link #frames()}
     * @return this track
     * @throws IllegalArgumentException if {@code start >= end} or the points are outside the track
     */
    Music setLoopPoints(long start, long end);
}
