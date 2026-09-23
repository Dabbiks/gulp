package dev.gulp.core;

import dev.gulp.api.Logger;
import dev.gulp.api.Owner;

/** What every core subsystem needs from the engine: owner state, loggers and the main-thread rule. */
public interface CoreContext {

    /**
     * Returns whether an owner may register listeners, tasks, commands and services right now: the game while the
     * engine runs, a module while it is enabled or being enabled, and the engine itself.
     *
     * @param owner the owner
     * @return {@code true} if registrations are accepted
     */
    boolean acceptsRegistrations(Owner owner);

    /**
     * Returns the logger of an owner, for error reports.
     *
     * @param owner the owner
     * @return its logger
     */
    Logger loggerOf(Owner owner);

    /**
     * Throws in development builds when called outside the main thread.
     *
     * @param method the API method, for the error message
     * @throws IllegalStateException if called from another thread in a development build
     */
    void checkMainThread(String method);

    /**
     * Rejects a registration by an inactive owner.
     *
     * @param owner the owner
     * @param what what is being registered, for the message
     * @throws IllegalStateException if the owner does not accept registrations
     */
    default void requireActive(Owner owner, String what) {
        if (!acceptsRegistrations(owner)) {
            throw new IllegalStateException("Cannot register " + what + " for '" + owner.id()
                    + "': it is not enabled. Register in onEnable (modules) or after onLoad (game).");
        }
    }
}
