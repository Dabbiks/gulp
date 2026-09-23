package dev.gulp.api.data;

import dev.gulp.api.event.Event;

/**
 * Fired after a config was reloaded from disk, for example by {@code /reload config}.
 *
 * <pre>{@code
 * on(ConfigReloadEvent.class, e -> { if (e.config() == config()) applySettings(); });
 * }</pre>
 */
public final class ConfigReloadEvent extends Event {

    private final Config config;

    /**
     * Creates the event; fired by the engine.
     *
     * @param config the reloaded config
     */
    public ConfigReloadEvent(Config config) {
        this.config = config;
    }

    /**
     * Returns the reloaded config.
     *
     * @return the config
     */
    public Config config() {
        return config;
    }
}
