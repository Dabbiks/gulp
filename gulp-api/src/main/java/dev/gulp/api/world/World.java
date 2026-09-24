package dev.gulp.api.world;

import dev.gulp.api.Owner;
import dev.gulp.api.audio.Playback;
import dev.gulp.api.audio.Sound;
import dev.gulp.api.data.DataContainer;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityQuery;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.event.Subscription;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Rng;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.nav.NavGrid;
import dev.gulp.api.physics.Physics;
import dev.gulp.api.render.Camera;
import dev.gulp.api.render.RenderLayer;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * A level or area: entities, a tile map, cameras, render layers and parallax backgrounds. Each game tick the active
 * world ticks its tiles and entity components; entities spawned during a tick start ticking on the next one, and
 * removed entities leave at its end.
 *
 * <pre>{@code
 * World world = worlds().create("arena", WorldSettings.DEFAULT);
 * Entity player = world.spawn(PLAYER, 5, 5, e -> e.setName("player"));
 * world.camera().follow(player);
 * world.query().tag("enemy").near(player.position(), 6).forEach(e -> e.setTint(Color.RED));
 * }</pre>
 */
public interface World {

    /**
     * Returns the name.
     *
     * @return the name
     */
    String name();

    /**
     * Returns the settings.
     *
     * @return the settings
     */
    WorldSettings settings();

    /**
     * Spawns an entity.
     *
     * @param type the type
     * @param x world units
     * @param y world units
     * @return the entity, or a removed entity if {@code EntitySpawnEvent} was cancelled
     */
    Entity spawn(EntityType type, float x, float y);

    /**
     * Spawns an entity.
     *
     * @param type the type
     * @param position world units
     * @return the entity
     */
    Entity spawn(EntityType type, Vec2 position);

    /**
     * Spawns an entity, configuring it before it enters the world and before {@code EntitySpawnEvent}.
     *
     * @param type the type
     * @param x world units
     * @param y world units
     * @param configure runs on the new entity
     * @return the entity
     */
    Entity spawn(EntityType type, float x, float y, Consumer<Entity> configure);

    /**
     * Spawns an entity at a location in this world.
     *
     * @param type the type
     * @param location the location
     * @param configure runs on the new entity
     * @return the entity
     * @throws IllegalArgumentException if the location is in another world
     */
    Entity spawn(EntityType type, Location location, Consumer<Entity> configure);

    /**
     * Returns the entities in the world.
     *
     * @return the entities, a snapshot in spawn order
     */
    List<Entity> entities();

    /**
     * Returns the number of entities in the world.
     *
     * @return the count
     */
    int entityCount();

    /**
     * Finds an entity by name.
     *
     * @param name the name
     * @return the entity, or {@code null}
     */
    @Nullable Entity entity(String name);

    /**
     * Finds an entity by id.
     *
     * @param id the id
     * @return the entity, or {@code null}
     */
    @Nullable Entity entity(UUID id);

    /**
     * Starts a query.
     *
     * @return a new query over this world
     */
    EntityQuery query();

    /**
     * Returns entities whose position is within a distance.
     *
     * @param center world units
     * @param radius world units
     * @return the entities
     */
    List<Entity> entitiesNear(Vec2 center, float radius);

    /**
     * Returns entities whose bounds overlap a rectangle.
     *
     * @param area world units
     * @return the entities
     */
    List<Entity> entitiesIn(Rect area);

    /**
     * Returns the tile map.
     *
     * @return the map
     */
    TileMap tileMap();

    /**
     * Returns the main camera, which covers the whole screen by default.
     *
     * @return the camera
     */
    Camera camera();

    /**
     * Returns every camera, the main one first.
     *
     * @return the cameras
     */
    List<Camera> cameras();

    /**
     * Adds a camera drawn into part of the screen, for split screen or a minimap.
     *
     * @param viewport the area of the screen, {@code 0..1} on both axes
     * @return the camera
     */
    Camera addCamera(Rect viewport);

    /**
     * Removes an added camera.
     *
     * @param camera the camera; the main camera cannot be removed
     */
    void removeCamera(Camera camera);

    /**
     * Returns the render layers of the world, back to front.
     *
     * @return the layers
     */
    List<RenderLayer> layers();

    /**
     * Adds a render layer to the world.
     *
     * @param name the name
     * @param zOrder draw order among world layers
     * @return the layer
     */
    RenderLayer addLayer(String name, int zOrder);

    /**
     * Returns the parallax backgrounds.
     *
     * @return the parallax
     */
    Parallax parallax();

    /**
     * Returns the physics of this world: gravity, collision matrix, queries and joints.
     *
     * @return the physics
     */
    Physics physics();

    /**
     * Returns the navigation grid, built from the collision tiles of the tile map.
     *
     * @return the grid
     */
    NavGrid navGrid();

    /**
     * Shows or hides a debug drawing, such as collision shapes or navigation paths.
     *
     * @param view what to draw
     * @param shown whether to draw it
     */
    void showDebug(DebugView view, boolean shown);

    /**
     * Returns whether a debug drawing is shown.
     *
     * @param view the drawing
     * @return {@code true} if drawn
     */
    boolean isDebugShown(DebugView view);

    /**
     * Returns the random generator of the world, seeded from its settings.
     *
     * @return the generator
     */
    Rng rng();

    /**
     * Returns the world data.
     *
     * @return the data container
     */
    DataContainer data();

    /**
     * Returns how many ticks this world has run.
     *
     * @return ticks
     */
    long ticks();

    /**
     * Returns a location in this world.
     *
     * @param x world units
     * @param y world units
     * @return the location
     */
    Location location(float x, float y);

    /**
     * Plays a sound at a position, heard from the main camera of the active world.
     *
     * @param position world units
     * @param sound the sound
     * @return the playback
     */
    Playback playSound(Vec2 position, Sound sound);

    /**
     * Keeps the chunks under an area loaded, for example around a player or a machine off screen.
     *
     * @param area world units
     * @param owner who keeps it; released automatically when the owner is disabled
     * @return cancelling the subscription releases the area
     */
    Subscription keepLoaded(Rect area, Owner owner);

    /**
     * Returns a loaded chunk.
     *
     * @param chunkX chunk column
     * @param chunkY chunk row
     * @return the chunk, or {@code null} if not loaded
     */
    @Nullable Chunk chunk(int chunkX, int chunkY);

    /**
     * Returns the loaded chunks.
     *
     * @return the chunks
     */
    List<Chunk> loadedChunks();

    /**
     * Returns whether the world is active.
     *
     * @return {@code true} for the active world
     */
    boolean isActive();

    /**
     * Returns whether the world is loaded.
     *
     * @return {@code false} after unload
     */
    boolean isLoaded();
}
