package dev.gulp.core.audio;

import dev.gulp.api.PauseMode;
import dev.gulp.api.audio.Playback;
import dev.gulp.api.audio.Sound;
import dev.gulp.platform.PlatformAudio;
import org.jspecify.annotations.Nullable;

/** A whole clip played from its buffer. */
final class ClipVoice extends Voice {

    private final PlatformAudio platform;
    final AudioClipImpl clip;

    ClipVoice(
            AudioImpl audio,
            PlatformAudio platform,
            int handle,
            BusImpl bus,
            AudioClipImpl clip,
            @Nullable Sound sound,
            PauseMode pauseMode,
            int priority,
            long order,
            float volume,
            float pitch) {
        super(audio, handle, bus, sound, pauseMode, priority, order, volume, pitch);
        this.platform = platform;
        this.clip = clip;
    }

    @Override
    boolean poll() {
        return platformPaused || platform.isPlaying(handle);
    }

    @Override
    void release() {
        platform.releaseVoice(handle);
    }

    @Override
    public Playback setLooping(boolean on) {
        if (looping != on && !ended) {
            looping = on;
            platform.setLooping(handle, on);
        }
        return this;
    }
}
