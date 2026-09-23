package dev.gulp.api.asset;

import dev.gulp.api.event.Event;
import java.util.List;

/**
 * Fired after the enabled resource packs changed and loaded assets were reloaded.
 *
 * <pre>{@code
 * on(ResourcePackChangeEvent.class, e -> logger().info("Packs: " + e.enabled()));
 * }</pre>
 */
public final class ResourcePackChangeEvent extends Event {

    private final List<ResourcePack> enabled;

    /**
     * Creates the event; fired by the engine.
     *
     * @param enabled the packs now enabled, highest priority first
     */
    public ResourcePackChangeEvent(List<ResourcePack> enabled) {
        this.enabled = List.copyOf(enabled);
    }

    /**
     * Returns the packs now enabled.
     *
     * @return the packs, highest priority first
     */
    public List<ResourcePack> enabled() {
        return enabled;
    }
}
