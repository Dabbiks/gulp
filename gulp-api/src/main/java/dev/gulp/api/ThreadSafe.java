package dev.gulp.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks API methods that may be called from any thread. Every other API method must be called on the main thread; in
 * development builds, calling it from another thread throws an exception naming the method and the thread.
 *
 * <pre>{@code
 * scheduler().async(() -> {
 *     Level level = LevelGenerator.generate(seed);   // background thread
 *     logger().info("Generated");                    // Logger is @ThreadSafe
 *     return level;
 * }).thenSync(world::load);                          // back on the main thread
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ThreadSafe {}
