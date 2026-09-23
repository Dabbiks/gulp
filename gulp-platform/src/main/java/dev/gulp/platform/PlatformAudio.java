package dev.gulp.platform;

import java.nio.ShortBuffer;

/**
 * PCM playback through a fixed pool of voices. {@code gulp-core} does all mixing decisions (buses, limits, positions)
 * and uses this interface only to play buffers.
 *
 * <pre>{@code
 * int buffer = audio.createBuffer(pcm, 2, 44_100);
 * int voice = audio.acquireVoice();
 * if (voice >= 0) {
 *     audio.setGain(voice, 0.8f);
 *     audio.play(voice, buffer, false);
 * }
 * }</pre>
 */
public interface PlatformAudio {

    /**
     * Returns whether sound can play. On the web audio stays locked until the first user gesture.
     *
     * @return {@code true} if unlocked
     */
    boolean isUnlocked();

    /**
     * Uploads 16-bit PCM samples.
     *
     * @param samples interleaved samples from position to limit
     * @param channels 1 or 2
     * @param sampleRate for example 44 100
     * @return the buffer handle
     */
    int createBuffer(ShortBuffer samples, int channels, int sampleRate);

    /**
     * Releases a buffer. It must not be playing or queued.
     *
     * @param buffer the handle
     */
    void deleteBuffer(int buffer);

    /**
     * Takes a free voice from the pool.
     *
     * @return the voice handle, or {@code -1} if all voices are busy
     */
    int acquireVoice();

    /**
     * Stops a voice and returns it to the pool.
     *
     * @param voice the handle
     */
    void releaseVoice(int voice);

    /**
     * Plays a whole buffer on a voice.
     *
     * @param voice the voice
     * @param buffer the buffer
     * @param loop whether to loop
     */
    void play(int voice, int buffer, boolean loop);

    /**
     * Appends a buffer to a streaming voice; playback starts when the first buffer is queued.
     *
     * @param voice the voice
     * @param buffer the buffer
     */
    void queue(int voice, int buffer);

    /**
     * Removes one buffer that finished playing from a streaming voice.
     *
     * @param voice the voice
     * @return the handle of the processed buffer, or {@code -1} if none has finished
     */
    int unqueueProcessed(int voice);

    /**
     * Pauses or resumes a voice.
     *
     * @param voice the voice
     * @param paused whether to pause
     */
    void setPaused(int voice, boolean paused);

    /**
     * Stops a voice without releasing it.
     *
     * @param voice the voice
     */
    void stop(int voice);

    /**
     * Returns whether a voice is playing.
     *
     * @param voice the voice
     * @return {@code true} while playing or paused
     */
    boolean isPlaying(int voice);

    /**
     * Sets voice volume.
     *
     * @param voice the voice
     * @param gain linear gain, {@code 0} silent, {@code 1} unchanged
     */
    void setGain(int voice, float gain);

    /**
     * Sets playback speed and pitch.
     *
     * @param voice the voice
     * @param pitch {@code 1} is unchanged, {@code 2} is an octave up
     */
    void setPitch(int voice, float pitch);

    /**
     * Sets stereo position.
     *
     * @param voice the voice
     * @param pan {@code -1} left, {@code 0} centre, {@code 1} right
     */
    void setPan(int voice, float pan);

    /**
     * Sets the master volume.
     *
     * @param gain linear gain
     */
    void setMasterGain(float gain);
}
