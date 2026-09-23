package dev.gulp.platform;

import org.jspecify.annotations.Nullable;

/**
 * Registry of code generated at compile time by {@code gulp-processor}: game entry point, event dispatchers and
 * component codecs. It replaces runtime reflection, which TeaVM does not support.
 *
 * <pre>{@code
 * ListenerDispatcher dispatcher = modules.lookup(ListenerDispatcher.class, DamageListener.class);
 * }</pre>
 */
public interface PlatformModules {

    /**
     * Finds generated code of one kind for one class.
     *
     * @param <T> the contract type
     * @param contract what kind of generated code is wanted, for example an event dispatcher interface
     * @param subject the class it was generated for
     * @return the generated implementation, or {@code null} if none was generated
     */
    <T> @Nullable T lookup(Class<T> contract, Class<?> subject);
}
