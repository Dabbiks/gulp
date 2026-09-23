package dev.gulp.api.service;

import dev.gulp.api.Owner;
import dev.gulp.api.event.Event;

/**
 * Fired after a service provider was registered.
 *
 * <pre>{@code
 * on(ServiceRegisterEvent.class, e -> { if (e.type() == Economy.class) shop.enable(); });
 * }</pre>
 */
public final class ServiceRegisterEvent extends Event {

    private final Class<?> type;
    private final Object provider;
    private final Owner owner;

    /**
     * Creates the event; fired by the engine.
     *
     * @param type the service interface
     * @param provider the implementation
     * @param owner the owner of the provider
     */
    public ServiceRegisterEvent(Class<?> type, Object provider, Owner owner) {
        this.type = type;
        this.provider = provider;
        this.owner = owner;
    }

    /**
     * Returns the service interface.
     *
     * @return the type
     */
    public Class<?> type() {
        return type;
    }

    /**
     * Returns the implementation.
     *
     * @return the provider
     */
    public Object provider() {
        return provider;
    }

    /**
     * Returns the owner of the provider.
     *
     * @return the owner
     */
    public Owner owner() {
        return owner;
    }
}
