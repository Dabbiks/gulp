package dev.gulp.api.entity;

import dev.gulp.api.Engine;
import dev.gulp.api.Gulp;
import dev.gulp.api.asset.Assets;
import dev.gulp.api.audio.Audio;
import dev.gulp.api.input.Input;
import dev.gulp.api.scheduler.Scheduler;
import dev.gulp.api.spi.ComponentAccess;
import dev.gulp.api.world.World;
import org.jspecify.annotations.Nullable;

/**
 * Behaviour or data attached to an entity. Entity types list component factories, so every entity gets its own
 * instances; components can also be added at run time with {@link Entity#add(Component)}.
 *
 * <p>Lifecycle: {@link #onAttach()} when added to an entity (it may not be in a world yet), {@link #onSpawn()} when the
 * entity enters the world, {@link #onTick()} every game tick while enabled, and {@link #onRemove()} when the entity or
 * the component is removed. Components of one class are stored together and ticked together, ordered by {@link
 * #tickOrder()}.
 *
 * <pre>{@code
 * public final class Spinner extends Component {
 *     private final float degreesPerTick;
 *
 *     public Spinner(float degreesPerTick) { this.degreesPerTick = degreesPerTick; }
 *
 *     @Override protected void onTick() {
 *         entity().setRotation(entity().rotation() + degreesPerTick);
 *     }
 * }
 * }</pre>
 */
public abstract class Component {

    static {
        ComponentAccess.install(new ComponentAccess.Hooks() {
            @Override
            public void bind(Component component, @Nullable Entity entity) {
                component.entity = entity;
            }

            @Override
            public void attach(Component component) {
                component.onAttach();
            }

            @Override
            public void spawn(Component component) {
                component.onSpawn();
            }

            @Override
            public void tick(Component component) {
                component.onTick();
            }

            @Override
            public void remove(Component component) {
                component.onRemove();
            }
        });
    }

    private @Nullable Entity entity;
    private boolean enabled = true;

    /** Creates the component. */
    protected Component() {}

    /** Called when the component is added to an entity, which may not be in a world yet. */
    protected void onAttach() {}

    /** Called when the entity enters its world, or at once when the component is added to a spawned entity. */
    protected void onSpawn() {}

    /** Called every game tick while the component is enabled and the entity is in a world. */
    protected void onTick() {}

    /** Called when the entity is removed or this component is removed from it. */
    protected void onRemove() {}

    /**
     * Returns the order among component classes in a tick: lower runs first.
     *
     * @return the order, {@code 0} by default
     */
    public int tickOrder() {
        return 0;
    }

    /**
     * Returns the entity.
     *
     * @return the entity
     * @throws IllegalStateException if the component is not attached
     */
    public final Entity entity() {
        Entity current = entity;
        if (current == null) {
            throw new IllegalStateException(getClass().getSimpleName() + " is not attached to an entity");
        }
        return current;
    }

    /**
     * Returns whether the component is attached to an entity.
     *
     * @return {@code true} between attach and removal
     */
    public final boolean isAttached() {
        return entity != null;
    }

    /**
     * Returns the world of the entity.
     *
     * @return the world
     */
    public final World world() {
        return entity().world();
    }

    /**
     * Returns a scheduler whose tasks are cancelled when the entity is removed.
     *
     * @return the scheduler
     */
    public final Scheduler scheduler() {
        return entity().scheduler();
    }

    /**
     * Returns whether {@link #onTick()} runs.
     *
     * @return {@code true} by default
     */
    public final boolean isEnabled() {
        return enabled;
    }

    /**
     * Turns ticking on or off.
     *
     * @param on whether to tick
     */
    public final void setEnabled(boolean on) {
        enabled = on;
    }

    /**
     * Returns the engine.
     *
     * @return the engine
     */
    protected final Engine engine() {
        return Gulp.engine();
    }

    /**
     * Returns player input.
     *
     * @return the input
     */
    protected final Input input() {
        return Gulp.engine().input();
    }

    /**
     * Returns sound and music.
     *
     * @return the audio
     */
    protected final Audio audio() {
        return Gulp.engine().audio();
    }

    /**
     * Returns the assets.
     *
     * @return the assets
     */
    protected final Assets assets() {
        return Gulp.engine().assets();
    }
}
