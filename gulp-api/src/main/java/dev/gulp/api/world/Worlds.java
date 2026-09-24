package dev.gulp.api.world;

import dev.gulp.api.scheduler.Promise;
import dev.gulp.api.ui.Transition;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * All worlds. Several worlds may be loaded at once; the active one is drawn and ticked, and the others tick only if
 * their settings ask for it.
 *
 * <pre>{@code
 * worlds().register("level2", WorldSource.tiled(GameAssets.Maps.LEVEL2).spawn("Player", PLAYER));
 * worlds().switchTo("level2", Transitions.fade(0.4f))
 *         .thenSync(world -> world.camera().follow(world.entity("player")));
 * }</pre>
 */
public interface Worlds {

    /**
     * Creates an empty world at once.
     *
     * @param name the name, unique among worlds
     * @param settings the settings
     * @return the world
     * @throws IllegalArgumentException if the name is taken
     */
    World create(String name, WorldSettings settings);

    /**
     * Loads a world; maps load their files first.
     *
     * @param name the name
     * @param source where it comes from
     * @return completes with the world after {@link WorldLoadEvent}
     */
    Promise<World> load(String name, WorldSource source);

    /**
     * Remembers a world to load the first time it is switched to.
     *
     * @param name the name
     * @param source where it comes from
     */
    void register(String name, WorldSource source);

    /**
     * Returns a loaded world.
     *
     * @param name the name
     * @return the world, or {@code null} if it is not loaded
     */
    @Nullable World get(String name);

    /**
     * Returns the loaded worlds.
     *
     * @return the worlds, in load order
     */
    List<World> all();

    /**
     * Returns the active world.
     *
     * @return the world, or {@code null} before the first switch
     */
    @Nullable World active();

    /**
     * Makes a world active at once, loading it first if it is registered.
     *
     * @param name the name
     * @return completes with the world after {@link WorldSwitchEvent}
     */
    Promise<World> switchTo(String name);

    /**
     * Makes a world active behind a transition: the old world fades out, the new one loads and fades in.
     *
     * @param name the name
     * @param transition the transition
     * @return completes with the world when the switch happened
     */
    Promise<World> switchTo(String name, Transition transition);

    /**
     * Unloads a world: its entities are removed and its chunks dropped. The active world cannot be unloaded.
     *
     * @param name the name
     * @throws IllegalStateException if it is the active world
     */
    void unload(String name);

    /**
     * Returns whether a transition is running.
     *
     * @return {@code true} during a transition
     */
    boolean isTransitioning();
}
