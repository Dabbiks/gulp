package dev.gulp.api.spi;

import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Lets the engine attach components to entities and run their lifecycle methods, which are protected so that game code
 * cannot call them. For {@code gulp-core} only.
 *
 * <pre>{@code
 * ComponentAccess.bind(component, entity);
 * ComponentAccess.tick(component);
 * }</pre>
 */
public final class ComponentAccess {

    /** Implemented inside {@link Component}. */
    public interface Hooks {
        /**
         * Sets the entity of a component.
         *
         * @param component the component
         * @param entity the entity, or {@code null} when detached
         */
        void bind(Component component, @Nullable Entity entity);

        /**
         * Calls {@code onAttach}.
         *
         * @param component the component
         */
        void attach(Component component);

        /**
         * Calls {@code onSpawn}.
         *
         * @param component the component
         */
        void spawn(Component component);

        /**
         * Calls {@code onTick}.
         *
         * @param component the component
         */
        void tick(Component component);

        /**
         * Calls {@code onRemove}.
         *
         * @param component the component
         */
        void remove(Component component);
    }

    private static @Nullable Hooks hooks;

    private ComponentAccess() {}

    /**
     * Installs the hooks; called once by {@link Component}.
     *
     * @param installed the hooks
     */
    public static void install(Hooks installed) {
        if (hooks == null) {
            hooks = installed;
        }
    }

    private static Hooks hooks() {
        Hooks current = hooks;
        if (current == null) {
            throw new IllegalStateException("Component hooks are not installed");
        }
        return current;
    }

    /**
     * Sets the entity of a component.
     *
     * @param component the component; creating it installed the hooks
     * @param entity the entity, or {@code null}
     */
    public static void bind(Component component, @Nullable Entity entity) {
        hooks().bind(component, entity);
    }

    /**
     * Calls {@code onAttach}.
     *
     * @param component the component
     */
    public static void attach(Component component) {
        hooks().attach(component);
    }

    /**
     * Calls {@code onSpawn}.
     *
     * @param component the component
     */
    public static void spawn(Component component) {
        hooks().spawn(component);
    }

    /**
     * Calls {@code onTick}.
     *
     * @param component the component
     */
    public static void tick(Component component) {
        hooks().tick(component);
    }

    /**
     * Calls {@code onRemove}.
     *
     * @param component the component
     */
    public static void remove(Component component) {
        hooks().remove(component);
    }
}
