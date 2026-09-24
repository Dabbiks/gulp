package dev.gulp.core.audio;

import dev.gulp.api.PauseMode;
import dev.gulp.api.audio.Playback;
import dev.gulp.api.audio.Sound;
import org.jspecify.annotations.Nullable;

/**
 * A playing sound on one platform voice. {@link AudioImpl} recomputes its gain, pan and filters every frame and applies
 * only what changed.
 */
abstract class Voice implements Playback {

    final AudioImpl audio;
    final int handle;
    final BusImpl bus;
    final @Nullable Sound sound;
    final PauseMode pauseMode;
    final int priority;
    final long order;

    float volume;
    float pitch;
    boolean looping;
    boolean positioned;
    float x;
    float y;
    float minDistance = 2f;
    float maxDistance = 25f;

    float fade = 1f;
    float fadeTarget = 1f;
    float fadeSpeed;
    boolean stopAfterFade;
    boolean userPaused;
    boolean platformPaused;
    boolean ended;

    float gain;
    float appliedGain = -1f;
    float appliedPan = Float.NaN;
    float appliedPitch = -1f;
    float appliedLowpass = -1f;
    float appliedHighpass = -1f;
    float appliedReverb = -1f;

    Voice(
            AudioImpl audio,
            int handle,
            BusImpl bus,
            @Nullable Sound sound,
            PauseMode pauseMode,
            int priority,
            long order,
            float volume,
            float pitch) {
        this.audio = audio;
        this.handle = handle;
        this.bus = bus;
        this.sound = sound;
        this.pauseMode = pauseMode;
        this.priority = priority;
        this.order = order;
        this.volume = volume;
        this.pitch = pitch;
    }

    /**
     * Advances playback: feeds streams and checks whether the sound ended.
     *
     * @return {@code false} once the sound is over
     */
    abstract boolean poll();

    /** Returns the platform voice and frees buffers. */
    abstract void release();

    void fadeIn(float seconds) {
        if (seconds > 0f) {
            fade = 0f;
            fadeTarget = 1f;
            fadeSpeed = 1f / seconds;
        }
    }

    void advanceFade(float seconds) {
        if (fade < fadeTarget) {
            fade = Math.min(fadeTarget, fade + fadeSpeed * seconds);
        } else if (fade > fadeTarget) {
            fade = Math.max(fadeTarget, fade - fadeSpeed * seconds);
        }
    }

    @Override
    public boolean isPlaying() {
        return !ended;
    }

    @Override
    public void stop() {
        if (!ended) {
            audio.finish(this, false);
        }
    }

    @Override
    public void fadeOut(float seconds) {
        if (seconds <= 0f) {
            stop();
            return;
        }
        fadeTarget = 0f;
        fadeSpeed = Math.max(fade, 1e-3f) / seconds;
        stopAfterFade = true;
    }

    @Override
    public float volume() {
        return volume;
    }

    @Override
    public Playback setVolume(float newVolume) {
        volume = Math.max(0f, newVolume);
        return this;
    }

    @Override
    public float pitch() {
        return pitch;
    }

    @Override
    public Playback setPitch(float newPitch) {
        if (!(newPitch > 0f)) {
            throw new IllegalArgumentException("Pitch must be positive: " + newPitch);
        }
        pitch = newPitch;
        return this;
    }

    @Override
    public boolean isLooping() {
        return looping;
    }

    @Override
    public Playback setPosition(float newX, float newY) {
        x = newX;
        y = newY;
        return this;
    }
}
