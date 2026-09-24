package dev.gulp.backend.headless;

import dev.gulp.platform.PlatformAudio;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Silent audio with counters and a simulated clock. Voices follow the same pool rules as real backends, and playback
 * advances with every frame by the frame time (scaled by pitch), so clips end, loops keep going and streams consume
 * their queued buffers as they would with sound.
 *
 * <pre>{@code
 * assertThat(backend.audio().playCount()).isEqualTo(1);
 * assertThat(backend.audio().gain(0)).isEqualTo(0.5f);
 * backend.audio().setUnlocked(false); // like a browser before the first click
 * }</pre>
 */
public final class HeadlessAudio implements PlatformAudio {

    /** Number of voices in the pool. */
    public static final int VOICES = 32;

    private final boolean[] acquired = new boolean[VOICES];
    private final boolean[] playing = new boolean[VOICES];
    private final boolean[] paused = new boolean[VOICES];
    private final boolean[] looping = new boolean[VOICES];
    private final float[] gain = new float[VOICES];
    private final float[] pitch = new float[VOICES];
    private final float[] pan = new float[VOICES];
    private final float[] lowpass = new float[VOICES];
    private final float[] highpass = new float[VOICES];
    private final float[] reverb = new float[VOICES];
    private final double[] remaining = new double[VOICES];
    private final int[] clip = new int[VOICES];
    private final Map<Integer, ArrayDeque<Integer>> queues = new HashMap<>();
    private final Map<Integer, ArrayDeque<Integer>> processed = new HashMap<>();
    private final Map<Integer, Double> durations = new HashMap<>();
    private int nextBuffer = 1;
    private int playCount;
    private float masterGain = 1f;
    private boolean unlocked = true;

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
     * Returns the pan of a voice.
     *
     * @param voice the voice
     * @return {@code -1..1}
     */
    public float pan(int voice) {
        return pan[voice];
    }

    /**
     * Returns the pitch of a voice.
     *
     * @param voice the voice
     * @return the pitch
     */
    public float pitch(int voice) {
        return pitch[voice];
    }

    /**
     * Returns the low-pass cutoff of a voice.
     *
     * @param voice the voice
     * @return hertz, 0 when off
     */
    public float lowpass(int voice) {
        return lowpass[voice];
    }

    /**
     * Returns the high-pass cutoff of a voice.
     *
     * @param voice the voice
     * @return hertz, 0 when off
     */
    public float highpass(int voice) {
        return highpass[voice];
    }

    /**
     * Returns the reverb send of a voice.
     *
     * @param voice the voice
     * @return {@code 0..1}
     */
    public float reverb(int voice) {
        return reverb[voice];
    }

    /**
     * Returns whether a voice is paused.
     *
     * @param voice the voice
     * @return {@code true} if paused
     */
    public boolean isPaused(int voice) {
        return paused[voice];
    }

    /**
     * Returns the number of voices taken from the pool.
     *
     * @return voices in use
     */
    public int voicesInUse() {
        int count = 0;
        for (boolean used : acquired) {
            if (used) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the master gain.
     *
     * @return the master gain
     */
    public float masterGain() {
        return masterGain;
    }

    /**
     * Locks or unlocks audio, to test the web behaviour before the first user gesture.
     *
     * @param value whether sound can play
     */
    public void setUnlocked(boolean value) {
        unlocked = value;
    }

    /**
     * Advances playback by a frame.
     *
     * @param seconds the frame time
     */
    void advance(double seconds) {
        for (int voice = 0; voice < VOICES; voice++) {
            if (!playing[voice] || paused[voice]) {
                continue;
            }
            double left = seconds * pitch[voice];
            ArrayDeque<Integer> queue = queues.get(voice);
            if (queue != null) {
                while (left > 0 && !queue.isEmpty()) {
                    double take = Math.min(left, remaining[voice]);
                    remaining[voice] -= take;
                    left -= take;
                    if (remaining[voice] <= 1e-9) {
                        processed
                                .computeIfAbsent(voice, v -> new ArrayDeque<>())
                                .add(queue.removeFirst());
                        remaining[voice] = queue.isEmpty() ? 0 : durations.getOrDefault(queue.peekFirst(), 0.0);
                    }
                }
                if (queue.isEmpty()) {
                    playing[voice] = false;
                }
            } else if (!looping[voice]) {
                remaining[voice] -= left;
                if (remaining[voice] <= 0) {
                    playing[voice] = false;
                }
            }
        }
    }

    @Override
    public boolean isUnlocked() {
        return unlocked;
    }

    @Override
    public int createBuffer(ShortBuffer samples, int channels, int sampleRate) {
        int buffer = nextBuffer++;
        durations.put(buffer, samples.remaining() / (double) channels / sampleRate);
        return buffer;
    }

    @Override
    public void deleteBuffer(int buffer) {
        durations.remove(buffer);
    }

    @Override
    public int acquireVoice() {
        for (int i = 0; i < VOICES; i++) {
            if (!acquired[i]) {
                acquired[i] = true;
                gain[i] = 1f;
                pitch[i] = 1f;
                pan[i] = 0f;
                lowpass[i] = 0f;
                highpass[i] = 0f;
                reverb[i] = 0f;
                paused[i] = false;
                return i;
            }
        }
        return -1;
    }

    @Override
    public void releaseVoice(int voice) {
        stop(voice);
        acquired[voice] = false;
    }

    @Override
    public void play(int voice, int buffer, boolean loop) {
        queues.remove(voice);
        clip[voice] = buffer;
        remaining[voice] = durations.getOrDefault(buffer, 0.0);
        looping[voice] = loop;
        playing[voice] = true;
        paused[voice] = false;
        playCount++;
    }

    @Override
    public void queue(int voice, int buffer) {
        ArrayDeque<Integer> queue = queues.computeIfAbsent(voice, v -> new ArrayDeque<>());
        if (queue.isEmpty()) {
            remaining[voice] = durations.getOrDefault(buffer, 0.0);
            playCount++;
        }
        queue.add(buffer);
        playing[voice] = true;
    }

    @Override
    public int unqueueProcessed(int voice) {
        ArrayDeque<Integer> done = processed.get(voice);
        return done == null || done.isEmpty() ? -1 : done.removeFirst();
    }

    @Override
    public void setPaused(int voice, boolean value) {
        paused[voice] = value;
    }

    @Override
    public void setLooping(int voice, boolean loop) {
        looping[voice] = loop;
    }

    @Override
    public void stop(int voice) {
        playing[voice] = false;
        paused[voice] = false;
        queues.remove(voice);
        processed.remove(voice);
    }

    @Override
    public boolean isPlaying(int voice) {
        return playing[voice];
    }

    @Override
    public void setGain(int voice, float value) {
        gain[voice] = value;
    }

    @Override
    public void setPitch(int voice, float value) {
        pitch[voice] = value;
    }

    @Override
    public void setPan(int voice, float value) {
        pan[voice] = value;
    }

    @Override
    public void setFilter(int voice, float lowpassHertz, float highpassHertz) {
        lowpass[voice] = lowpassHertz;
        highpass[voice] = highpassHertz;
    }

    @Override
    public void setReverb(int voice, float send) {
        reverb[voice] = send;
    }

    @Override
    public void setMasterGain(float value) {
        this.masterGain = value;
    }
}
