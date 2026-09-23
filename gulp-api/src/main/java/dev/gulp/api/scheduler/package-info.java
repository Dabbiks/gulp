/**
 * Scheduler: delayed, repeating and sequenced tasks on the main thread, and asynchronous work with promises.
 *
 * <pre>{@code
 * later(Duration.ofSeconds(2), () -> door.close());
 * }</pre>
 */
@NullMarked
package dev.gulp.api.scheduler;

import org.jspecify.annotations.NullMarked;
