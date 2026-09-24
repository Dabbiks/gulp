package dev.gulp.core.audio;

import dev.gulp.api.audio.Audio;
import dev.gulp.api.audio.Bus;
import dev.gulp.api.data.Preferences;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** A bus: volume, mute, filters, reverb and ducking of other buses. Volume and mute are kept in preferences. */
final class BusImpl implements Bus {

    /** How fast ducking moves, in volume per second. */
    private static final float DUCK_SPEED = 3f;

    private final String name;
    private final @Nullable BusImpl master;
    private final Preferences preferences;
    final Map<String, Float> ducks = new LinkedHashMap<>();
    private float volume = 1f;
    private boolean muted;
    private float lowpass;
    private float highpass;
    private float reverb;
    private float duck = 1f;
    float duckTarget = 1f;
    int playing;

    BusImpl(String name, @Nullable BusImpl master, Preferences preferences) {
        this.name = name;
        this.master = master;
        this.preferences = preferences;
    }

    void loadSettings() {
        volume = clamp(preferences.getFloat(key("volume"), volume));
        muted = preferences.getBoolean(key("muted"), muted);
    }

    private String key(String what) {
        return "audio." + name + "." + what;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    void updateDuck(float seconds) {
        if (duck < duckTarget) {
            duck = Math.min(duckTarget, duck + DUCK_SPEED * seconds);
        } else if (duck > duckTarget) {
            duck = Math.max(duckTarget, duck - DUCK_SPEED * seconds);
        }
    }

    float effectiveLowpass() {
        float own = lowpass;
        float parent = master != null ? master.lowpass : 0f;
        if (own <= 0f) {
            return parent;
        }
        return parent <= 0f ? own : Math.min(own, parent);
    }

    float effectiveHighpass() {
        return Math.max(highpass, master != null ? master.highpass : 0f);
    }

    float effectiveReverb() {
        return Math.max(reverb, master != null ? master.reverb : 0f);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public float volume() {
        return volume;
    }

    @Override
    public void setVolume(float newVolume) {
        volume = clamp(newVolume);
        preferences.set(key("volume"), volume);
    }

    @Override
    public boolean isMuted() {
        return muted;
    }

    @Override
    public void setMuted(boolean on) {
        muted = on;
        preferences.set(key("muted"), on);
    }

    @Override
    public float lowpass() {
        return lowpass;
    }

    @Override
    public void setLowpass(float hertz) {
        lowpass = Math.max(0f, hertz);
    }

    @Override
    public float highpass() {
        return highpass;
    }

    @Override
    public void setHighpass(float hertz) {
        highpass = Math.max(0f, hertz);
    }

    @Override
    public float reverb() {
        return reverb;
    }

    @Override
    public void setReverb(float amount) {
        reverb = clamp(amount);
    }

    @Override
    public void duck(String otherBus, float factor) {
        if (otherBus.equals(name) || otherBus.equals(Audio.MASTER)) {
            throw new IllegalArgumentException("A bus cannot duck itself or master");
        }
        ducks.put(otherBus, clamp(factor));
    }

    @Override
    public void stopDucking(String otherBus) {
        ducks.remove(otherBus);
    }

    @Override
    public float effectiveVolume() {
        if (muted) {
            return 0f;
        }
        float value = volume * duck;
        return master != null ? value * master.effectiveVolume() : value;
    }

    @Override
    public String toString() {
        return "Bus[" + name + ", " + volume + (muted ? ", muted" : "") + "]";
    }
}
