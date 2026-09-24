package dev.gulp.api.entity.component;

import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.math.Vec2;
import org.jspecify.annotations.Nullable;

/**
 * Moves the entity towards another one every tick, for pets, homing missiles and name plates that lag behind. Unlike
 * {@link Entity#attach}, the follower moves on its own and can be slowed down or smoothed.
 *
 * <pre>{@code
 * pet.add(new Follow(player).offset(-1, 0).speed(6f));
 * }</pre>
 */
public final class Follow extends Component {

    private @Nullable Entity target;
    private Vec2 offset = Vec2.ZERO;
    private float speed = Float.POSITIVE_INFINITY;
    private float stopDistance;
    private boolean removeWithTarget;

    /**
     * Creates the component.
     *
     * @param target the entity to follow
     */
    public Follow(Entity target) {
        this.target = target;
    }

    @Override
    public int tickOrder() {
        return 1000;
    }

    @Override
    protected void onTick() {
        Entity followed = target;
        if (followed == null) {
            return;
        }
        if (followed.isRemoved()) {
            target = null;
            if (removeWithTarget) {
                entity().remove();
            }
            return;
        }
        Entity self = entity();
        float tx = followed.x() + offset.x();
        float ty = followed.y() + offset.y();
        float dx = tx - self.x();
        float dy = ty - self.y();
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance <= stopDistance || distance == 0f) {
            return;
        }
        float step = speed / engine().targetTps();
        float travel = Math.min(distance - stopDistance, step);
        self.setPosition(self.x() + dx / distance * travel, self.y() + dy / distance * travel);
    }

    /**
     * Returns the followed entity.
     *
     * @return the entity, or {@code null} after it was removed
     */
    public @Nullable Entity target() {
        return target;
    }

    /**
     * Follows another entity.
     *
     * @param value the entity, or {@code null} to stop
     * @return this component
     */
    public Follow target(@Nullable Entity value) {
        this.target = value;
        return this;
    }

    /**
     * Keeps a distance from the target's position.
     *
     * @param x world units
     * @param y world units
     * @return this component
     */
    public Follow offset(float x, float y) {
        this.offset = new Vec2(x, y);
        return this;
    }

    /**
     * Limits the speed.
     *
     * @param unitsPerSecond the speed, unlimited by default
     * @return this component
     */
    public Follow speed(float unitsPerSecond) {
        this.speed = unitsPerSecond;
        return this;
    }

    /**
     * Stops short of the target.
     *
     * @param distance world units
     * @return this component
     */
    public Follow stopDistance(float distance) {
        this.stopDistance = Math.max(0f, distance);
        return this;
    }

    /**
     * Removes this entity when the target is removed.
     *
     * @param value whether to remove it
     * @return this component
     */
    public Follow removeWithTarget(boolean value) {
        this.removeWithTarget = value;
        return this;
    }
}
