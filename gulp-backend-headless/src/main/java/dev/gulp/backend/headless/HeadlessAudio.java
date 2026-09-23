package dev.gulp.backend.headless;

import dev.gulp.platform.PlatformAudio;
import java.nio.ShortBuffer;

/**
 * Silent audio with counters. Keeps the same voice-pool rules as real backends so tests can check limits.
 *
 * <pre>{@code
 * assertThat(backend.audio().playCount()).isEqualTo(1);
 * }</pre>
 */
public final class HeadlessAudio implements PlatformAudio {

    /** Number of voices in the pool. */
    public static final int VOICES = 32;

    private final boolean[] acquired = new boolean[VOICES];
    private final boolean[] playing = new boolean[VOICES];
    private final float[] gain = new float[VOICES];
    private int nextBuffer = 1;
    private int playCount;
    private float masterGain = 1f;

    HeadlessAudio() {}

    /**
     * Returns how many times {@link #play(int, int, boolean)} or {@link #queue(int, int)} was called.
     *
     * @return the play count
     */
    public int playCount() {
        return playCount;
    }

    /**
     * Returns the gain of a voice.
     *
     * @param voice the voice
     * @return its gain
     */
    public float gain(int voice) {
        return gain[voice];
    }

    /**
     * Returns the master gain.
     *
     * @return the master gain
     */
    public float masterGain() {
        return masterGain;
    }

    @Override
    public boolean isUnlocked() {
        return true;
    }

    @Override
    public int createBuffer(ShortBuffer samples, int channels, int sampleRate) {
        return nextBuffer++;
    }

    @Override
    public void deleteBuffer(int buffer) {}

    @Override
    public int acquireVoice() {
        for (int i = 0; i < VOICES; i++) {
            if (!acquired[i]) {
                acquired[i] = true;
                gain[i] = 1f;
                return i;
            }
        }
        return -1;
    }

    @Override
    public void releaseVoice(int voice) {
        acquired[voice] = false;
        playing[voice] = false;
    }

    @Override
    public void play(int voice, int buffer, boolean loop) {
        playing[voice] = true;
        playCount++;
    }

    @Override
    public void queue(int voice, int buffer) {
        playing[voice] = true;
        playCount++;
    }

    @Override
    public int unqueueProcessed(int voice) {
        return -1;
    }

    @Override
    public void setPaused(int voice, boolean paused) {}

    @Override
    public void stop(int voice) {
        playing[voice] = false;
    }

    @Override
    public boolean isPlaying(int voice) {
        return playing[voice];
    }

    @Override
    public void setGain(int voice, float gain) {
        this.gain[voice] = gain;
    }

    @Override
    public void setPitch(int voice, float pitch) {}

    @Override
    public void setPan(int voice, float pan) {}

    @Override
    public void setMasterGain(float gain) {
        this.masterGain = gain;
    }
}
