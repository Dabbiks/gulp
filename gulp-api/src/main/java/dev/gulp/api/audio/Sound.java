package dev.gulp.api.audio;

import dev.gulp.api.PauseMode;
import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.registry.Key;
import dev.gulp.api.registry.Keyed;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A sound effect: one or more short files (a random one plays each time), random volume and pitch ranges, a limit of
 * simultaneous instances and a minimum interval, so thirty coins picked up at once do not play thirty sounds. Register
 * sounds in {@code Registries.SOUND} during {@code onLoad}; their files load with the startup group.
 *
 * <pre>{@code
 * PICKUP = registries().register(Registries.SOUND, Sound.builder(key("pickup"))
 *         .file(GameAssets.Sounds.PICKUP)
 *         .pitchRange(0.9f, 1.1f)
 *         .maxInstances(4)
 *         .build());
 * audio().play(PICKUP);
 * }</pre>
 */
public final class Sound implements Keyed {

    private final Key key;
    private final List<AssetKey<AudioClip>> files;
    private final float minVolume;
    private final float maxVolume;
    private final float minPitch;
    private final float maxPitch;
    private final int maxInstances;
    private final float minInterval;
    private final String bus;
    private final int priority;
    private final PauseMode pauseMode;
    private final float minDistance;
    private final float maxDistance;

    private Sound(Builder builder) {
        if (builder.files.isEmpty()) {
            throw new IllegalStateException("Sound " + builder.key + " has no file");
        }
        this.key = builder.key;
        this.files = Collections.unmodifiableList(new ArrayList<>(builder.files));
        this.minVolume = builder.minVolume;
        this.maxVolume = builder.maxVolume;
        this.minPitch = builder.minPitch;
        this.maxPitch = builder.maxPitch;
        this.maxInstances = builder.maxInstances;
        this.minInterval = builder.minInterval;
        this.bus = builder.bus;
        this.priority = builder.priority;
        this.pauseMode = builder.pauseMode != null
                ? builder.pauseMode
                : Audio.UI.equals(builder.bus) ? PauseMode.ALWAYS : PauseMode.GAME;
        this.minDistance = builder.minDistance;
        this.maxDistance = builder.maxDistance;
    }

    /**
     * Starts building a sound.
     *
     * @param key the sound key
     * @return the builder
     */
    public static Builder builder(Key key) {
        return new Builder(key);
    }

    @Override
    public Key key() {
        return key;
    }

    /**
     * Returns the files; one is picked at random each time.
     *
     * @return the files
     */
    public List<AssetKey<AudioClip>> files() {
        return files;
    }

    /**
     * Returns the lowest random volume.
     *
     * @return linear volume
     */
    public float minVolume() {
        return minVolume;
    }

    /**
     * Returns the highest random volume.
     *
     * @return linear volume
     */
    public float maxVolume() {
        return maxVolume;
    }

    /**
     * Returns the lowest random pitch.
     *
     * @return pitch, {@code 1} unchanged
     */
    public float minPitch() {
        return minPitch;
    }

    /**
     * Returns the highest random pitch.
     *
     * @return pitch
     */
    public float maxPitch() {
        return maxPitch;
    }

    /**
     * Returns how many instances may play at once; further plays are skipped.
     *
     * @return the limit, {@code 0} for none
     */
    public int maxInstances() {
        return maxInstances;
    }

    /**
     * Returns the minimum time between two starts; plays sooner are skipped.
     *
     * @return seconds
     */
    public float minInterval() {
        return minInterval;
    }

    /**
     * Returns the bus the sound plays on.
     *
     * @return the bus name
     */
    public String bus() {
        return bus;
    }

    /**
     * Returns the priority when voices run out: a sound never replaces one with a higher priority.
     *
     * @return the priority
     */
    public int priority() {
        return priority;
    }

    /**
     * Returns whether the sound pauses with the game.
     *
     * @return the pause mode
     */
    public PauseMode pauseMode() {
        return pauseMode;
    }

    /**
     * Returns the distance from the listener up to which a positioned sound plays at full volume.
     *
     * @return world units
     */
    public float minDistance() {
        return minDistance;
    }

    /**
     * Returns the distance from the listener at which a positioned sound becomes silent.
     *
     * @return world units
     */
    public float maxDistance() {
        return maxDistance;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Sound sound && sound.key.equals(key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return "Sound[" + key + "]";
    }

    /**
     * Builds a {@link Sound}.
     *
     * <pre>{@code
     * Sound step = Sound.builder(key("step"))
     *         .file(GameAssets.Sounds.STEP_1).file(GameAssets.Sounds.STEP_2)
     *         .volumeRange(0.6f, 0.8f).minInterval(0.08f).build();
     * }</pre>
     */
    public static final class Builder {
        private final Key key;
        private final List<AssetKey<AudioClip>> files = new ArrayList<>();
        private float minVolume = 1f;
        private float maxVolume = 1f;
        private float minPitch = 1f;
        private float maxPitch = 1f;
        private int maxInstances;
        private float minInterval = 0.03f;
        private String bus = Audio.SFX;
        private int priority;
        private @Nullable PauseMode pauseMode;
        private float minDistance = 2f;
        private float maxDistance = 25f;

        private Builder(Key key) {
            this.key = key;
        }

        /**
         * Adds a file; with several files a random one plays.
         *
         * @param file the audio clip key
         * @return this builder
         */
        public Builder file(AssetKey<AudioClip> file) {
            files.add(file);
            return this;
        }

        /**
         * Sets a fixed volume.
         *
         * @param volume linear volume, {@code 0..1} usually
         * @return this builder
         */
        public Builder volume(float volume) {
            return volumeRange(volume, volume);
        }

        /**
         * Sets a random volume range.
         *
         * @param min the lowest volume
         * @param max the highest volume
         * @return this builder
         * @throws IllegalArgumentException if the range is empty or negative
         */
        public Builder volumeRange(float min, float max) {
            if (!(min >= 0f && max >= min)) {
                throw new IllegalArgumentException("Invalid volume range " + min + ".." + max);
            }
            this.minVolume = min;
            this.maxVolume = max;
            return this;
        }

        /**
         * Sets a fixed pitch.
         *
         * @param pitch the pitch, {@code 1} unchanged
         * @return this builder
         */
        public Builder pitch(float pitch) {
            return pitchRange(pitch, pitch);
        }

        /**
         * Sets a random pitch range, for variety.
         *
         * @param min the lowest pitch
         * @param max the highest pitch
         * @return this builder
         * @throws IllegalArgumentException if the range is empty or not positive
         */
        public Builder pitchRange(float min, float max) {
            if (!(min > 0f && max >= min)) {
                throw new IllegalArgumentException("Invalid pitch range " + min + ".." + max);
            }
            this.minPitch = min;
            this.maxPitch = max;
            return this;
        }

        /**
         * Limits simultaneous instances.
         *
         * @param limit the limit, {@code 0} for none
         * @return this builder
         */
        public Builder maxInstances(int limit) {
            if (limit < 0) {
                throw new IllegalArgumentException("Instance limit must not be negative: " + limit);
            }
            this.maxInstances = limit;
            return this;
        }

        /**
         * Sets the minimum time between starts.
         *
         * @param seconds the interval, {@code 0.03} by default
         * @return this builder
         */
        public Builder minInterval(float seconds) {
            if (!(seconds >= 0f)) {
                throw new IllegalArgumentException("Interval must not be negative: " + seconds);
            }
            this.minInterval = seconds;
            return this;
        }

        /**
         * Chooses the bus.
         *
         * @param busName the bus, {@link Audio#SFX} by default
         * @return this builder
         */
        public Builder bus(String busName) {
            this.bus = busName;
            return this;
        }

        /**
         * Sets the priority when voices run out.
         *
         * @param value the priority, {@code 0} by default
         * @return this builder
         */
        public Builder priority(int value) {
            this.priority = value;
            return this;
        }

        /**
         * Chooses whether the sound pauses with the game. By default sounds on the UI bus keep playing and others
         * pause.
         *
         * @param mode the mode
         * @return this builder
         */
        public Builder pauseMode(PauseMode mode) {
            this.pauseMode = mode;
            return this;
        }

        /**
         * Sets how positioned sounds fade with distance from the listener.
         *
         * @param min full volume up to this distance
         * @param max silent from this distance
         * @return this builder
         * @throws IllegalArgumentException if {@code max <= min} or {@code min < 0}
         */
        public Builder distance(float min, float max) {
            if (!(min >= 0f && max > min)) {
                throw new IllegalArgumentException("Invalid distance range " + min + ".." + max);
            }
            this.minDistance = min;
            this.maxDistance = max;
            return this;
        }

        /**
         * Builds the sound.
         *
         * @return the sound
         * @throws IllegalStateException if no file was given
         */
        public Sound build() {
            return new Sound(this);
        }
    }
}
