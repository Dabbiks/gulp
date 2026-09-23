/**
 * Events: discrete things that happened, delivered to handlers by priority.
 *
 * <pre>{@code
 * on(ModuleEnableEvent.class, e -> logger().info("Enabled " + e.moduleId()));
 * }</pre>
 */
@NullMarked
package dev.gulp.api.event;

import org.jspecify.annotations.NullMarked;
