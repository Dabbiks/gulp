package dev.gulp.api.event.lifecycle;

import dev.gulp.api.event.Event;

/**
 * Fired when game time resumes with {@code Engine.resume()}.
 *
 * <pre>{@code
 * on(ResumeEvent.class, e -> music.unduck());
 * }</pre>
 */
public final class ResumeEvent extends Event {

    /** Creates the event; fired by the engine. */
    public ResumeEvent() {}
}
