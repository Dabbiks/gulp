package dev.gulp.api.audio;

import dev.gulp.api.math.Vec2;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Sound and music. Sounds play on voices from a pool (32 by default); when it is full the quietest, oldest sound of
 * the lowest priority stops, unless the new one has an even lower priority. On the web audio unlocks with the first
 * click or key press; sounds played before that are skipped.
 *
 * <pre>{@code
 * audio().play(PICKUP);
 * audio().play(HIT, 0.8f, 1.2f);
 * audio().playAt(EXPLOSION, barrel.x(), barrel.y());   // quieter and panned by distance from the camera
 * audio().bus(Audio.SFX).setVolume(0.7f);
 * audio().music().play(GameAssets.Music.THEME, 2f);
 * }</pre>
 */
public interface Audio {

    /** The bus every other bus feeds. */
    String MASTER = "master";

    /** Music bus. */
    String MUSIC = "music";

    /** Sound effects bus, the default for sounds. */
    String SFX = "sfx";

    /** Interface sounds; they keep playing while the game is paused. */
    String UI = "ui";

    /** Dialogue; ducks the music while it plays. */
    String VOICE = "voice";

    /**
     * Plays a sound with its own random volume and pitch.
     *
     * @param sound the sound
     * @return the playback
     */
    Playback play(Sound sound);

    /**
     * Plays a sound, multiplying its random volume and pitch.
     *
     * @param sound the sound
     * @param volume volume factor
     * @param pitch pitch factor
     * @return the playback
     */
    Playback play(Sound sound, float volume, float pitch);

    /**
     * Plays a sound at a world position: quieter with distance from the listener and panned left or right.
     *
     * @param sound the sound
     * @param x world position
     * @param y world position
     * @return the playback
     */
    Playback playAt(Sound sound, float x, float y);

    /**
     * Plays a clip directly, without a {@link Sound} definition.
     *
     * @param clip the clip
     * @param bus the bus name
     * @return the playback
     */
    Playback play(AudioClip clip, String bus);

    /**
     * Plays procedural samples until the source ends or the playback stops.
     *
     * @param source the source
     * @param bus the bus name
     * @return the playback
     */
    Playback stream(PcmSource source, String bus);

    /**
     * Returns the music player.
     *
     * @return the player
     */
    MusicPlayer music();

    /**
     * Returns a bus.
     *
     * @param name the name, one of the constants or a bus made with {@link #createBus(String)}
     * @return the bus
     * @throws IllegalArgumentException if no such bus exists
     */
    Bus bus(String name);

    /**
     * Creates a bus feeding master, or returns the existing one.
     *
     * @param name the name, {@code [a-z0-9_.-]+}
     * @return the bus
     */
    Bus createBus(String name);

    /**
     * Returns every bus, master first.
     *
     * @return the buses
     */
    List<Bus> buses();

    /**
     * Places the listener for positioned sounds.
     *
     * @param position world position, or {@code null} to follow the display camera again (the default)
     */
    void setListener(@Nullable Vec2 position);

    /**
     * Returns the listener position.
     *
     * @return world position
     */
    Vec2 listener();

    /**
     * Returns whether sound can play; on the web {@code false} until the first user gesture.
     *
     * @return {@code true} if unlocked
     */
    boolean isUnlocked();

    /**
     * Returns the number of sounds playing, music included.
     *
     * @return voices in use
     */
    int activeVoices();

    /**
     * Returns the size of the voice pool.
     *
     * @return the voice limit
     */
    int maxVoices();

    /** Stops every sound; music keeps playing. */
    void stopAll();
}
