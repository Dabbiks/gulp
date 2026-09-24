package dev.gulp.api.audio;

/**
 * A volume bus. Every sound plays on one bus, and every bus feeds {@code master}. Volumes and mute state are saved in
 * {@code preferences()} automatically.
 *
 * <pre>{@code
 * audio().bus(Audio.MUSIC).setVolume(0.6f);
 * on(PauseEvent.class, e -> audio().bus(Audio.SFX).setLowpass(800));  // muffled while paused
 * on(ResumeEvent.class, e -> audio().bus(Audio.SFX).setLowpass(0));
 * audio().bus(Audio.VOICE).duck(Audio.MUSIC, 0.35f);                  // the default
 * }</pre>
 */
public interface Bus {

    /**
     * Returns the name.
     *
     * @return the name
     */
    String name();

    /**
     * Returns the volume set on this bus.
     *
     * @return linear volume, {@code 0..1}
     */
    float volume();

    /**
     * Changes the volume.
     *
     * @param volume linear volume, {@code 0..1}
     */
    void setVolume(float volume);

    /**
     * Returns whether the bus is muted.
     *
     * @return {@code true} if muted
     */
    boolean isMuted();

    /**
     * Mutes or unmutes the bus.
     *
     * @param muted whether to mute
     */
    void setMuted(boolean muted);

    /**
     * Returns the low-pass cutoff: frequencies above it are cut.
     *
     * @return hertz, {@code 0} when off
     */
    float lowpass();

    /**
     * Sets the low-pass cutoff, for example 800 Hz to muffle sounds under water.
     *
     * @param hertz the cutoff, {@code 0} to turn off
     */
    void setLowpass(float hertz);

    /**
     * Returns the high-pass cutoff: frequencies below it are cut.
     *
     * @return hertz, {@code 0} when off
     */
    float highpass();

    /**
     * Sets the high-pass cutoff, for example 1500 Hz for a radio voice.
     *
     * @param hertz the cutoff, {@code 0} to turn off
     */
    void setHighpass(float hertz);

    /**
     * Returns how much of the bus goes to the reverb.
     *
     * @return {@code 0..1}
     */
    float reverb();

    /**
     * Sets how much of the bus goes to the reverb, for caves and halls.
     *
     * @param amount {@code 0} dry, {@code 1} fully wet
     */
    void setReverb(float amount);

    /**
     * Lowers another bus while anything plays on this one, and restores it afterwards.
     *
     * @param otherBus the bus to lower
     * @param volume its volume factor while ducked, {@code 0..1}
     */
    void duck(String otherBus, float volume);

    /**
     * Stops lowering another bus.
     *
     * @param otherBus the bus
     */
    void stopDucking(String otherBus);

    /**
     * Returns the volume sounds on this bus get: its own volume, master and ducking, or {@code 0} when muted.
     *
     * @return linear volume
     */
    float effectiveVolume();
}
