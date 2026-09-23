package dev.gulp.backend.headless;

import dev.gulp.core.GeneratedModules;
import dev.gulp.platform.PlatformModules;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Registry of generated code: the code {@code gulp-processor} generated for the classpath, plus entries a test
 * registers by hand, which take precedence.
 *
 * <pre>{@code
 * backend.modules().register(ModuleDescriptor.class, FakeModule.class, new ModuleDescriptor("fake", ...));
 * }</pre>
 */
public final class HeadlessModules implements PlatformModules {

    private final Map<Class<?>, Map<Class<?>, Object>> entries = new HashMap<>();

    HeadlessModules() {}

    /**
     * Registers generated code by hand.
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
        Object manual = bySubject == null ? null : bySubject.get(subject);
        return manual != null
                ? contract.cast(manual)
                : GeneratedModules.shared().lookup(contract, subject);
    }
}
