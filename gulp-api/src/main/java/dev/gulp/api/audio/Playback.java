package dev.gulp.api.audio;

/**
 * One playing sound. A playback that was skipped (instance limit, locked web audio, no free voice) is returned too; it
 * simply reports {@link #isPlaying()} as {@code false}, so callers never check for {@code null}.
 *
 * <pre>{@code
 * Playback engineHum = audio().play(ENGINE).setLooping(true);
 * engineHum.setPitch(0.8f + speed * 0.05f);
 * engineHum.fadeOut(0.5f);
 * }</pre>
 */
public interface Playback {

    /**
     * Returns whether the sound is still playing (or paused with the game).
     *
     * @return {@code false} after it ended or stopped
     */
    boolean isPlaying();

    /** Stops at once. */
    void stop();

    /**
     * Fades the volume to zero and stops.
     *
     * @param seconds fade length
     */
    void fadeOut(float seconds);

    /**
     * Returns the volume of this playback, before bus volumes.
     *
     * @return linear volume
     */
    float volume();

    /**
     * Changes the volume.
     *
     * @param volume linear volume
     * @return this playback
     */
    Playback setVolume(float volume);

    /**
     * Returns the pitch.
     *
     * @return pitch, {@code 1} unchanged
     */
    float pitch();

    /**
     * Changes the pitch and speed.
     *
     * @param pitch the pitch, {@code 2} is an octave up
     * @return this playback
     */
    Playback setPitch(float pitch);

    /**
     * Returns whether the sound loops.
     *
     * @return {@code true} if looping
     */
    boolean isLooping();

    /**
     * Chooses whether the sound loops. Streams ({@link Audio#stream}) ignore this.
     *
     * @param looping whether to loop
     * @return this playback
     */
    Playback setLooping(boolean looping);

    /**
     * Moves a positioned sound (one started with {@link Audio#playAt}).
     *
     * @param x world position
     * @param y world position
     * @return this playback
     */
    Playback setPosition(float x, float y);
}
