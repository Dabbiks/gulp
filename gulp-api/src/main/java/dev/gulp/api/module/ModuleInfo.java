package dev.gulp.api.module;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a module's id and dependencies. Read at compile time by {@code gulp-processor}, which validates the ids and
 * generates the runtime descriptor (no reflection at runtime).
 *
 * <pre>{@code
 * @ModuleInfo(id = "coin", dependsOn = {"player", "hud"})
 * public final class CoinModule extends GameModule { ... }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface ModuleInfo {

    /**
     * Returns the module id, unique within the game.
     *
     * @return an id matching {@code [a-z0-9_.-]+}
     */
    String id();

    /**
     * Returns the modules that must be enabled before this one. A missing or failed dependency keeps this module
     * disabled.
     *
     * @return ids of required modules
     */
    String[] dependsOn() default {};

    /**
     * Returns modules that, if present, are loaded and enabled before this one.
     *
     * @return ids of optional modules
     */
    String[] softDependsOn() default {};

    /**
     * Returns whether the module is enabled at startup. {@code onLoad} runs either way.
     *
     * @return {@code true} by default
     */
    boolean enabledByDefault() default true;
}
