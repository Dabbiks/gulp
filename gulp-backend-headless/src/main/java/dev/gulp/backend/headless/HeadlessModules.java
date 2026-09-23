package dev.gulp.backend.headless;

import dev.gulp.platform.PlatformModules;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Registry of generated code filled by hand. Until the annotation processor exists (stage 1) tests register
 * implementations directly.
 *
 * <pre>{@code
 * backend.modules().register(Dispatcher.class, MyListener.class, new MyListener$Handlers());
 * }</pre>
 */
public final class HeadlessModules implements PlatformModules {

    private final Map<Class<?>, Map<Class<?>, Object>> entries = new HashMap<>();

    HeadlessModules() {}

    /**
     * Registers generated code.
     *
     * @param <T> the contract type
     * @param contract the kind of generated code
     * @param subject the class it belongs to
     * @param implementation the implementation
     */
    public <T> void register(Class<T> contract, Class<?> subject, T implementation) {
        entries.computeIfAbsent(contract, c -> new HashMap<>()).put(subject, implementation);
    }

    @Override
    public <T> @Nullable T lookup(Class<T> contract, Class<?> subject) {
        Map<Class<?>, Object> bySubject = entries.get(contract);
        return bySubject == null ? null : contract.cast(bySubject.get(subject));
    }
}
