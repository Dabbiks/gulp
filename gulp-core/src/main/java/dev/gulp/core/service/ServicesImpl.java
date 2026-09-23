package dev.gulp.core.service;

import dev.gulp.api.Owner;
import dev.gulp.api.event.Events;
import dev.gulp.api.service.ServicePriority;
import dev.gulp.api.service.ServiceRegisterEvent;
import dev.gulp.api.service.ServiceUnregisterEvent;
import dev.gulp.api.service.Services;
import dev.gulp.core.CoreContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** {@link Services} with providers sorted by priority, highest first, stable within a priority. */
public final class ServicesImpl implements Services {

    private record Provider(Class<?> type, Object provider, Owner owner, ServicePriority priority) {}

    private final CoreContext context;
    private final Events events;
    private final Map<Class<?>, List<Provider>> providers = new HashMap<>();

    /**
     * Creates the registry.
     *
     * @param context owner state and main-thread checks
     * @param events fires register and unregister events
     */
    public ServicesImpl(CoreContext context, Events events) {
        this.context = context;
        this.events = events;
    }

    @Override
    public <T> void register(Class<T> type, T provider, Owner owner, ServicePriority priority) {
        context.checkMainThread("Services.register");
        context.requireActive(owner, "service " + type.getSimpleName());
        if (!type.isInstance(provider)) {
            throw new IllegalArgumentException(provider.getClass().getName() + " does not implement " + type.getName());
        }
        List<Provider> list = providers.computeIfAbsent(type, k -> new ArrayList<>());
        int index = 0;
        while (index < list.size() && list.get(index).priority.compareTo(priority) >= 0) {
            index++;
        }
        list.add(index, new Provider(type, provider, owner, priority));
        events.call(new ServiceRegisterEvent(type, provider, owner));
    }

    @Override
    public <T> boolean unregister(Class<T> type, T provider) {
        context.checkMainThread("Services.unregister");
        List<Provider> list = providers.get(type);
        if (list == null) {
            return false;
        }
        for (int i = 0; i < list.size(); i++) {
            Provider entry = list.get(i);
            if (entry.provider == provider) {
                list.remove(i);
                events.call(new ServiceUnregisterEvent(type, provider, entry.owner));
                return true;
            }
        }
        return false;
    }

    /**
     * Removes every provider of an owner; called when it is disabled.
     *
     * @param owner the owner
     */
    public void unregisterAll(Owner owner) {
        List<Provider> removed = new ArrayList<>();
        for (List<Provider> list : providers.values()) {
            for (int i = list.size() - 1; i >= 0; i--) {
                if (list.get(i).owner == owner) {
                    removed.add(list.remove(i));
                }
            }
        }
        for (Provider entry : removed) {
            events.call(new ServiceUnregisterEvent(entry.type, entry.provider, entry.owner));
        }
    }

    @Override
    public <T> @Nullable T get(Class<T> type) {
        List<Provider> list = providers.get(type);
        return list == null || list.isEmpty() ? null : type.cast(list.getFirst().provider);
    }

    @Override
    public <T> T getOrThrow(Class<T> type) {
        T provider = get(type);
        if (provider == null) {
            throw new IllegalStateException("No provider of service " + type.getName() + " is registered");
        }
        return provider;
    }

    @Override
    public <T> List<T> getAll(Class<T> type) {
        List<Provider> list = providers.get(type);
        if (list == null) {
            return List.of();
        }
        List<T> result = new ArrayList<>(list.size());
        for (Provider entry : list) {
            result.add(type.cast(entry.provider));
        }
        return List.copyOf(result);
    }

    @Override
    public boolean isProvided(Class<?> type) {
        List<Provider> list = providers.get(type);
        return list != null && !list.isEmpty();
    }
}
