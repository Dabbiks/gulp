package dev.gulp.api.audio;

import dev.gulp.api.event.Event;

/**
 * A music track played to its end: a track that does not loop, or a track of a playlist.
 *
 * <pre>{@code
 * on(MusicEndEvent.class, e -> audio().music().play(nextTrack(), 0f));
 * }</pre>
 */
public final class MusicEndEvent extends Event {

    private final Music music;

    /**
     * Creates the event.
     *
     * @param music the track that ended
     */
    public MusicEndEvent(Music music) {
        this.music = music;
    }

    /**
     * Returns the track that ended.
     *
     * @return the track
     */
    public Music music() {
        return music;
    }
}
