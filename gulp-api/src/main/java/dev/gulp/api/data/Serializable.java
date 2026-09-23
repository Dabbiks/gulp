package dev.gulp.api.data;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Asks {@code gulp-processor} to generate a {@link Codec} for a record, available through {@link Codec#of(Class)}.
 *
 * <p>Supported component types: primitives and their wrappers, {@link String}, {@link dev.gulp.api.registry.Key},
 * enums, {@link JsonValue}, {@link java.util.List} and {@link java.util.Map} with {@code String} keys of supported
 * types, and other {@code @Serializable} records. Components annotated {@code @Nullable} may be missing or {@code null}
 * in JSON.
 *
 * <pre>{@code
 * @Serializable
 * public record Upgrade(String name, int level, @Nullable String icon) {}
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Serializable {}
