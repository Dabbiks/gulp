package dev.gulp.api.spi;

import dev.gulp.api.Engine;
import org.jspecify.annotations.Nullable;

/**
 * Holds the engine returned by {@link dev.gulp.api.Gulp#engine()}. Called only by {@code gulp-core} when an engine
 * starts and stops; game code never calls it.
 *
 * <pre>{@code
 * EngineBinding.bind(engine);
 * try { ... } finally { EngineBinding.unbind(engine); }
 * }</pre>
 */
public final class EngineBinding {

    private static @Nullable Engine current;

    private EngineBinding() {}

    /**
     * Returns the bound engine.
     *
     * @return the engine, or {@code null} if none runs
     */
    public static @Nullable Engine current() {
        return current;
    }

    /**
     * Binds a starting engine.
     *
     * @param engine the engine
     * @throws IllegalStateException if another engine is bound
     */
    public static void bind(Engine engine) {
        if (current != null && current != engine) {
            throw new IllegalStateException("Another Gulp engine is already running in this process");
        }
        current = engine;
    }

    /**
     * Unbinds a stopped engine. Does nothing if a different engine is bound.
     *
     * @param engine the engine that stopped
     */
    public static void unbind(Engine engine) {
        if (current == engine) {
            current = null;
        }
    }
}
