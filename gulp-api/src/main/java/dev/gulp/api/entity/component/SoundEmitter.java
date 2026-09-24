package dev.gulp.api.entity.component;

import dev.gulp.api.audio.Playback;
import dev.gulp.api.audio.Sound;
import dev.gulp.api.entity.Component;
import org.jspecify.annotations.Nullable;

/**
 * Plays a sound at the entity's position and moves it along with the entity, for engines, torches or waterfalls. The
 * sound stops when the entity is removed.
 *
 * <pre>{@code
 * EntityType.builder(key("campfire"))
 *         .component(() -> new SoundEmitter(CRACKLE).loop(true).volume(0.6f))
 *         .build();
 * }</pre>
 */
public final class SoundEmitter extends Component {

    private Sound sound;
    private boolean loop;
    private boolean playOnSpawn = true;
    private float volume = 1f;
    private @Nullable Playback playback;

    /**
     * Creates an emitter that plays the sound when the entity spawns.
     *
     * @param sound the sound
     */
    public SoundEmitter(Sound sound) {
        this.sound = sound;
    }

    /**
     * Returns the sound.
     *
     * @return the sound
     */
    public Sound sound() {
        return sound;
    }

    /**
     * Changes the sound used by the next {@link #play()}.
     *
     * @param value the sound
     * @return this emitter
     */
    public SoundEmitter sound(Sound value) {
        sound = value;
        return this;
    }

    /**
     * Makes the sound repeat until stopped.
     *
     * @param value whether to loop
     * @return this emitter
     */
    public SoundEmitter loop(boolean value) {
        loop = value;
        return this;
    }

    /**
     * Returns whether the sound loops.
     *
     * @return {@code true} if looping
     */
    public boolean isLooping() {
        return loop;
    }

    /**
     * Sets whether the sound starts when the entity spawns.
     *
     * @param value {@code true} by default
     * @return this emitter
     */
    public SoundEmitter playOnSpawn(boolean value) {
        playOnSpawn = value;
        return this;
    }

    /**
     * Sets the volume of the sounds it plays.
     *
     * @param value {@code 0..1} on top of the sound's own volume
     * @return this emitter
     */
    public SoundEmitter volume(float value) {
        volume = value;
        Playback current = playback;
        if (current != null) {
            current.setVolume(value);
        }
        return this;
    }

    /**
     * Starts the sound again at the entity, stopping the previous one.
     *
     * @return the playback
     */
    public Playback play() {
        stop();
        Playback started = audio().playAt(sound, entity().x(), entity().y());
        started.setVolume(volume);
        if (loop) {
            started.setLooping(true);
        }
        playback = started;
        return started;
    }

    /** Stops the sound. */
    public void stop() {
        Playback current = playback;
        if (current != null) {
            current.stop();
            playback = null;
        }
    }

    /**
     * Returns whether the emitter's sound is playing.
     *
     * @return {@code true} while playing
     */
    public boolean isPlaying() {
        Playback current = playback;
        return current != null && current.isPlaying();
    }

    @Override
    protected void onSpawn() {
        if (playOnSpawn) {
            play();
        }
    }

    @Override
    protected void onTick() {
        Playback current = playback;
        if (current != null && current.isPlaying()) {
            current.setPosition(entity().x(), entity().y());
        }
    }

    @Override
    protected void onRemove() {
        stop();
    }
}
