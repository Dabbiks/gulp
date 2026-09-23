package dev.gulp.api.service;

import dev.gulp.api.Owner;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Registry of services shared between modules without direct dependencies: one module provides an interface, others
 * look it up. A provider disappears when its owner is disabled.
 *
 * <pre>{@code
 * // provider module
 * services().register(Economy.class, new SimpleEconomy(), this, ServicePriority.NORMAL);
 *
 * // consumer module
 * Economy economy = services().get(Economy.class);
 * if (economy != null) economy.deposit(player, 10);
 * }</pre>
 */
public interface Services {

    /**
     * Registers a provider. Fires {@link ServiceRegisterEvent}.
     *
     * @param <T> the service type
     * @param type the service interface
     * @param provider the implementation
     * @param owner the owner; the provider is removed when it is disabled
     * @param priority the priority among providers of the same type
     */
    <T> void register(Class<T> type, T provider, Owner owner, ServicePriority priority);

    /**
     * Removes a provider. Fires {@link ServiceUnregisterEvent}.
     *
     * @param <T> the service type
     * @param type the service interface
     * @param provider the implementation to remove
     * @return {@code true} if it was registered
     */
    <T> boolean unregister(Class<T> type, T provider);

    /**
     * Returns the provider with the highest priority; among equal priorities, the first registered.
     *
     * @param <T> the service type
     * @param type the service interface
     * @return the provider, or {@code null} if none is registered
     */
    <T> @Nullable T get(Class<T> type);

    /**
     * Returns the provider with the highest priority.
     *
     * @param <T> the service type
     * @param type the service interface
     * @return the provider
     * @throws IllegalStateException if none is registered
     */
    <T> T getOrThrow(Class<T> type);

    /**
     * Returns every provider of a type, highest priority first.
     *
     * @param <T> the service type
     * @param type the service interface
     * @return an unmodifiable list, possibly empty
     */
    <T> List<T> getAll(Class<T> type);

    /**
     * Returns whether any provider is registered.
     *
     * @param type the service interface
     * @return {@code true} if at least one provider exists
     */
    boolean isProvided(Class<?> type);
}
