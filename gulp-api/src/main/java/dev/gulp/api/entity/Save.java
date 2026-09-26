package dev.gulp.api.entity;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field of a persistent component ({@link ComponentInfo}) to be saved. Fields must not be private or final;
 * supported types are primitives, {@code String}, {@code Key}, {@code Vec2}, enums, {@code JsonValue}, lists, maps
 * with string keys and {@code @Serializable} records.
 *
 * <pre>{@code
 * @Save int coins;
 * @Save Vec2 checkpoint = Vec2.ZERO;
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface Save {}
