package dev.gulp.api.entity;

import dev.gulp.api.PauseMode;
import dev.gulp.api.data.DataContainer;
import dev.gulp.api.event.Event;
import dev.gulp.api.event.Subscription;
import dev.gulp.api.event.TargetedEvent;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.scheduler.Scheduler;
import dev.gulp.api.world.Location;
import dev.gulp.api.world.World;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * An object in a world: a position, a look, components, tags and data. Entities are created by {@link
 * World#spawn(EntityType, float, float)} and live until {@link #remove()}; removal takes effect at the end of the tick.
 *
 * <p>Positions are in world units (1 = tile) and refer to the centre of the entity; {@link #size()} spans half each
 * way. Rendering interpolates between the last two ticks; spawning and {@link #teleport} skip interpolation, so the
 * entity does not slide across the screen.
 *
 * <pre>{@code
 * Entity slime = world.spawn(SLIME, 4, 2, e -> e.tags().add("boss"));
 * slime.get(Health.class).damage(3);
 * slime.on(EntityRemoveEvent.class, e -> logger().info("The slime is gone"));
 * slime.attach(world.spawn(CROWN, 0, 0), new Vec2(0, -0.6f));
 * }</pre>
 */
public interface Entity {

    /**
     * Returns the unique id, kept in saves.
     *
     * @return the id
     */
    UUID id();

    /**
     * Returns a small number unique among the living entities of this run, for fast lookups.
     *
     * @return the runtime id, from 1
     */
    int runtimeId();

    /**
     * Returns the type.
     *
     * @return the type
     */
    EntityType type();

    /**
     * Returns the world.
     *
     * @return the world
     */
    World world();

    /**
     * Returns the name, unique within the world.
     *
     * @return the name, or {@code null}
     */
    @Nullable String name();

    /**
     * Names the entity so that {@link World#entity(String)} finds it.
     *
     * @param name the name, or {@code null} to remove it
     * @return this entity
     * @throws IllegalArgumentException if another entity in the world has that name
     */
    Entity setName(@Nullable String name);

    /**
     * Returns the position of the centre.
     *
     * @return world units
     */
    Vec2 position();

    /**
     * Returns the horizontal position without creating a vector.
     *
     * @return world units
     */
    float x();

    /**
     * Returns the vertical position without creating a vector.
     *
     * @return world units, down is positive
     */
    float y();

    /**
     * Moves the entity; rendering interpolates the motion.
     *
     * @param x world units
     * @param y world units
     * @return this entity
     */
    Entity setPosition(float x, float y);

    /**
     * Moves the entity; rendering interpolates the motion.
     *
     * @param position world units
     * @return this entity
     */
    Entity setPosition(Vec2 position);

    /**
     * Moves the entity at once, without interpolation, possibly to another world. Fires {@link EntityTeleportEvent}.
     *
     * @param location the target
     * @return this entity
     */
    Entity teleport(Location location);

    /**
     * Moves the entity at once within its world, without interpolation.
     *
     * @param x world units
     * @param y world units
     * @return this entity
     */
    Entity teleport(float x, float y);

    /**
     * Returns the rotation.
     *
     * @return degrees, clockwise
     */
    float rotation();

    /**
     * Rotates the entity.
     *
     * @param degrees degrees, clockwise
     * @return this entity
     */
    Entity setRotation(float degrees);

    /**
     * Returns the scale of the look.
     *
     * @return the scale
     */
    Vec2 scale();

    /**
     * Scales the look.
     *
     * @param x horizontal factor
     * @param y vertical factor
     * @return this entity
     */
    Entity setScale(float x, float y);

    /**
     * Returns the size of the bounds.
     *
     * @return world units
     */
    Vec2 size();

    /**
     * Changes the size of the bounds.
     *
     * @param width world units
     * @param height world units
     * @return this entity
     */
    Entity setSize(float width, float height);

    /**
     * Returns the bounds: the size centred on the position.
     *
     * @return world units
     */
    Rect bounds();

    /**
     * Returns the render layer name.
     *
     * @return the layer, {@code entities} by default
     */
    String layer();

    /**
     * Moves the entity to another render layer.
     *
     * @param layer the layer name
     * @return this entity
     */
    Entity setLayer(String layer);

    /**
     * Returns the draw order within the layer; ties are drawn by position, lower first.
     *
     * @return the order
     */
    int zIndex();

    /**
     * Changes the draw order within the layer.
     *
     * @param zIndex the order
     * @return this entity
     */
    Entity setZIndex(int zIndex);

    /**
     * Returns whether the entity is drawn.
     *
     * @return {@code true} by default
     */
    boolean isVisible();

    /**
     * Shows or hides the entity; it keeps ticking.
     *
     * @param visible whether to draw
     * @return this entity
     */
    Entity setVisible(boolean visible);

    /**
     * Returns the colour multiplied into the look.
     *
     * @return the tint, white by default
     */
    Color tint();

    /**
     * Changes the tint.
     *
     * @param tint the colour
     * @return this entity
     */
    Entity setTint(Color tint);

    /**
     * Returns whether the look is mirrored horizontally.
     *
     * @return {@code true} if flipped
     */
    boolean isFlipX();

    /**
     * Mirrors the look horizontally.
     *
     * @param flip whether to mirror
     * @return this entity
     */
    Entity setFlipX(boolean flip);

    /**
     * Returns whether the look is mirrored vertically.
     *
     * @return {@code true} if flipped
     */
    boolean isFlipY();

    /**
     * Mirrors the look vertically.
     *
     * @param flip whether to mirror
     * @return this entity
     */
    Entity setFlipY(boolean flip);

    /**
     * Attaches a child that follows this entity at an offset, like a rider or a weapon.
     *
     * @param child the child, in the same world
     * @param offset position relative to this entity
     * @return this entity
     * @throws IllegalArgumentException if the child is this entity, an ancestor, or in another world
     */
    Entity attach(Entity child, Vec2 offset);

    /**
     * Detaches this entity from its parent; it stays where it is.
     *
     * @return this entity
     */
    Entity detach();

    /**
     * Returns the parent.
     *
     * @return the parent, or {@code null}
     */
    @Nullable Entity parent();

    /**
     * Returns the attached children.
     *
     * @return the children, a snapshot
     */
    List<Entity> children();

    /**
     * Returns whether children are detached instead of removed when this entity is removed.
     *
     * @return {@code false} by default: children are removed too
     */
    boolean isDetachOnRemove();

    /**
     * Chooses whether children survive the removal of this entity.
     *
     * @param detach {@code true} to detach them instead of removing them
     * @return this entity
     */
    Entity setDetachOnRemove(boolean detach);

    /**
     * Returns a component.
     *
     * @param <C> the component type
     * @param type the class, or a superclass of the component
     * @return the component
     * @throws IllegalStateException if the entity has no such component
     */
    <C extends Component> C get(Class<C> type);

    /**
     * Returns a component if present.
     *
     * @param <C> the component type
     * @param type the class, or a superclass of the component
     * @return the component
     */
    <C extends Component> Optional<C> find(Class<C> type);

    /**
     * Returns whether the entity has a component.
     *
     * @param type the class, or a superclass of the component
     * @return {@code true} if present
     */
    boolean has(Class<? extends Component> type);

    /**
     * Adds a component; if the entity is spawned, {@code onSpawn} runs at once.
     *
     * @param <C> the component type
     * @param component the component, not attached elsewhere
     * @return the component
     * @throws IllegalArgumentException if a component of the same class is present or it is attached elsewhere
     */
    <C extends Component> C add(C component);

    /**
     * Removes a component.
     *
     * @param type the class of the component
     * @return {@code true} if one was removed
     */
    boolean remove(Class<? extends Component> type);

    /**
     * Returns every component.
     *
     * @return the components, a snapshot in the order they were added
     */
    List<Component> components();

    /**
     * Returns the tags.
     *
     * @return the tags, live
     */
    Tags tags();

    /**
     * Returns the data container.
     *
     * @return the data
     */
    DataContainer data();

    /**
     * Returns whether the entity is saved with its world.
     *
     * @return {@code true} if persistent
     */
    boolean isPersistent();

    /**
     * Chooses whether the entity is saved with its world.
     *
     * @param persistent whether to save
     * @return this entity
     */
    Entity setPersistent(boolean persistent);

    /**
     * Returns whether the entity keeps ticking while the game is paused.
     *
     * @return the pause mode
     */
    PauseMode pauseMode();

    /**
     * Chooses whether the entity keeps ticking while the game is paused.
     *
     * @param mode the mode
     * @return this entity
     */
    Entity setPauseMode(PauseMode mode);

    /**
     * Subscribes to events about this entity only; the subscription ends when the entity is removed.
     *
     * @param <E> the event type
     * @param type the event class, for example {@link EntityRemoveEvent}
     * @param handler the handler
     * @return the subscription
     */
    <E extends Event & TargetedEvent> Subscription on(Class<E> type, Consumer<? super E> handler);

    /**
     * Returns a scheduler whose tasks are cancelled when the entity is removed.
     *
     * @return the scheduler
     */
    Scheduler scheduler();

    /** Removes the entity at the end of the tick, with its children unless {@link #isDetachOnRemove()}. */
    void remove();

    /**
     * Returns whether {@link #remove()} was called.
     *
     * @return {@code true} once removal was asked for
     */
    boolean isRemoved();

    /**
     * Returns whether the entity is in its world (spawned and not yet removed).
     *
     * @return {@code true} while in the world
     */
    boolean isSpawned();

    /**
     * Returns whether the bounds overlap the view of a camera of the active world, as of the last tick.
     *
     * @return {@code true} if visible on screen
     */
    boolean isOnScreen();
}
