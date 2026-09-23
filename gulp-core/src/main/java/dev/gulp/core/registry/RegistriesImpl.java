package dev.gulp.core.registry;

import dev.gulp.api.registry.Key;
import dev.gulp.api.registry.Keyed;
import dev.gulp.api.registry.Registries;
import dev.gulp.api.registry.Registry;
import dev.gulp.api.registry.RegistryKey;
import dev.gulp.core.CoreContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/** {@link Registries} with the built-in registries; everything freezes at the end of the load phase. */
public final class RegistriesImpl implements Registries {

    private final CoreContext context;
    private final Map<Key, RegistryImpl<?>> registries = new LinkedHashMap<>();
    private boolean frozen;

    /**
     * Creates the registries with every built-in one.
     *
     * @param context main-thread checks
     */
    public RegistriesImpl(CoreContext context) {
        this.context = context;
        for (RegistryKey<?> builtIn : java.util.List.of(
                ENTITY_TYPE,
                TILE_TYPE,
                COMPONENT_TYPE,
                SOUND,
                PARTICLE_EFFECT,
                INPUT_ACTION,
                COLLISION_LAYER,
                DAMAGE_TYPE,
                TRANSITION,
                THEME)) {
            add(builtIn);
        }
    }

    private <T extends Keyed> RegistryImpl<T> add(RegistryKey<T> key) {
        RegistryImpl<T> registry = new RegistryImpl<>(key);
        registries.put(key.key(), registry);
        return registry;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Keyed> Registry<T> get(RegistryKey<T> key) {
        RegistryImpl<?> registry = registries.get(key.key());
        if (registry == null) {
            throw new IllegalArgumentException("No registry " + key);
        }
        if (registry.registryKey().type() != key.type()) {
            throw new IllegalArgumentException("Registry " + key + " holds "
                    + registry.registryKey().type().getName() + ", not "
                    + key.type().getName());
        }
        return (Registry<T>) registry;
    }

    @Override
    public @Nullable Registry<?> get(Key key) {
        return registries.get(key);
    }

    @Override
    public <T extends Keyed> Registry<T> create(Key key, Class<T> type) {
        context.checkMainThread("Registries.create");
        if (frozen) {
            throw new IllegalStateException("Registries are frozen; create registry " + key + " in onLoad()");
        }
        if (key.isReserved()) {
            throw new IllegalArgumentException("Namespace '" + Key.RESERVED + "' is reserved for the engine: " + key);
        }
        if (registries.containsKey(key)) {
            throw new IllegalArgumentException("Registry " + key + " already exists");
        }
        return add(RegistryKey.of(key, type));
    }

    @Override
    public Collection<Registry<?>> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(registries.values()));
    }

    @Override
    public boolean isFrozen() {
        return frozen;
    }

    /** Ends the load phase: no registry accepts new values afterwards. */
    public void freeze() {
        frozen = true;
    }

    /** One registry. */
    final class RegistryImpl<T extends Keyed> implements Registry<T> {
        private final RegistryKey<T> key;
        private final Map<Key, T> values = new LinkedHashMap<>();

        RegistryImpl(RegistryKey<T> key) {
            this.key = key;
        }

        @Override
        public RegistryKey<T> registryKey() {
            return key;
        }

        @Override
        public T register(Key valueKey, T value) {
            context.checkMainThread("Registry.register");
            if (frozen) {
                throw new IllegalStateException(
                        "Registry " + key + " is frozen; register " + valueKey + " in onLoad()");
            }
            if (valueKey.isReserved()) {
                throw new IllegalArgumentException(
                        "Namespace '" + Key.RESERVED + "' is reserved for the engine: " + valueKey);
            }
            if (!valueKey.equals(value.key())) {
                throw new IllegalArgumentException(
                        "Key " + valueKey + " differs from the value's own key " + value.key());
            }
            if (!key.type().isInstance(value)) {
                throw new IllegalArgumentException("Registry " + key + " holds "
                        + key.type().getName() + ", got " + value.getClass().getName());
            }
            if (values.containsKey(valueKey)) {
                throw new IllegalArgumentException(valueKey + " is already registered in " + key);
            }
            values.put(valueKey, value);
            return value;
        }

        @Override
        public @Nullable T get(Key valueKey) {
            return values.get(valueKey);
        }

        @Override
        public T getOrThrow(Key valueKey) {
            T value = values.get(valueKey);
            if (value == null) {
                throw new IllegalArgumentException("Nothing registered as " + valueKey + " in " + key);
            }
            return value;
        }

        @Override
        public boolean contains(Key valueKey) {
            return values.containsKey(valueKey);
        }

        @Override
        public Set<Key> keys() {
            return Collections.unmodifiableSet(values.keySet());
        }

        @Override
        public Collection<T> values() {
            return Collections.unmodifiableCollection(values.values());
        }

        @Override
        public Stream<T> stream() {
            return values.values().stream();
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public boolean isFrozen() {
            return frozen;
        }

        @Override
        public java.util.Iterator<T> iterator() {
            return values().iterator();
        }

        @Override
        public String toString() {
            return "Registry " + key + " (" + values.size() + ")";
        }
    }
}
