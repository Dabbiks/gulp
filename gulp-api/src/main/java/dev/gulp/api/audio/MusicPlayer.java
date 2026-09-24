package dev.gulp.api.audio;

import dev.gulp.api.asset.AssetKey;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Plays one music track at a time on the music bus, with fades, crossfades and playlists. On the web, music asked for
 * before the first click or key press starts as soon as audio unlocks.
 *
 * <pre>{@code
 * audio().music().play(GameAssets.Music.MENU, 1f);
 * audio().music().crossfadeTo(assets().get(GameAssets.Music.BATTLE), 2f);
 * audio().music().playlist(List.of(track1, track2, track3), true, 3f);
 * on(MusicEndEvent.class, e -> logger().info("Finished " + e.music().name()));
 * }</pre>
 */
public interface MusicPlayer {

    /**
     * Plays a track, stopping the current one at once.
     *
     * @param music the track
     * @param fadeInSeconds fade-in length, {@code 0} for none
     */
    void play(Music music, float fadeInSeconds);

    /**
     * Loads a track if needed and plays it when ready.
     *
     * @param music the track key
     * @param fadeInSeconds fade-in length
     */
    void play(AssetKey<Music> music, float fadeInSeconds);

    /**
     * Fades the current track out while the new one fades in.
     *
     * @param music the new track
     * @param seconds crossfade length
     */
    void crossfadeTo(Music music, float seconds);

    /**
     * Plays tracks one after another, ignoring their loop flags, and starts over after the last.
     *
     * @param tracks the tracks
     * @param shuffle whether to play them in random order (reshuffled every round)
     * @param crossfadeSeconds overlap between tracks, {@code 0} for none
     */
    void playlist(List<Music> tracks, boolean shuffle, float crossfadeSeconds);

    /**
     * Stops the music.
     *
     * @param fadeOutSeconds fade-out length, {@code 0} to stop at once
     */
    void stop(float fadeOutSeconds);

    /** Pauses the music. */
    void pause();

    /** Resumes paused music. */
    void resume();

    /**
     * Returns the track playing, or fading in.
     *
     * @return the track, or {@code null}
     */
    @Nullable Music current();

    /**
     * Returns whether music plays (or waits for the web audio unlock).
     *
     * @return {@code true} if playing
     */
    boolean isPlaying();

    /**
     * Returns the position in the current track.
     *
     * @return seconds from the start
     */
    float position();

    /**
     * Returns the volume of the player, below the music bus.
     *
     * @return linear volume
     */
    float volume();

    /**
     * Changes the volume of the player.
     *
     * @param volume linear volume
     */
    void setVolume(float volume);
}
