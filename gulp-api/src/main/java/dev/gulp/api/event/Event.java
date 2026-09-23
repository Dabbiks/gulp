package dev.gulp.api.event;

/**
 * Base class of all events. Events describe discrete things that happened (a module was enabled, an entity took
 * damage); continuous per-tick logic belongs in components, not in events.
 *
 * <p>No handler-list boilerplate is needed: the engine keeps handler lists per event class, and a listener of a base
 * class receives events of all subclasses.
 *
 * <pre>{@code
 * public final class ScoreChangedEvent extends Event {
 *     private final int score;
 *     public ScoreChangedEvent(int score) { this.score = score; }
 *     public int score() { return score; }
 * }
 *
 * events().call(new ScoreChangedEvent(42));
 * }</pre>
 */
public abstract class Event {

    /** Creates an event. */
    protected Event() {}

    /**
     * Returns a short name for logs and error chains.
     *
     * @return the simple class name
     */
    public String eventName() {
        return getClass().getSimpleName();
    }
}
