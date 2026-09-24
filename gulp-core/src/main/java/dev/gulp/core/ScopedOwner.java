package dev.gulp.core;

import dev.gulp.api.Owner;

/**
 * An owner that lives inside the game, such as an entity: its handlers and tasks are accepted while it is enabled and
 * removed when it ends.
 */
public interface ScopedOwner extends Owner {}
