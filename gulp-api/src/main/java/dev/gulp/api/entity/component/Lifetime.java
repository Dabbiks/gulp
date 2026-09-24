package dev.gulp.api.entity.component;

import dev.gulp.api.Gulp;
import dev.gulp.api.entity.Component;
import java.time.Duration;

/**
 * Removes the entity after a number of game ticks, for bullets, effects and pickups that expire.
 *
 * <pre>{@code
 * EntityType.builder(key("spark")).component(() -> new Lifetime(Duration.ofMillis(400))).build();
 * }</pre>
 */
public final class Lifetime extends Component {

    private final long total;
    private long remaining;

    /**
     * Creates the component.
     *
     * @param ticks game ticks until removal, at least 1
     */
    public Lifetime(long ticks) {
        if (ticks < 1) {
            throw new IllegalArgumentException("Lifetime must be at least one tick: " + ticks);
        }
        this.total = ticks;
        this.remaining = ticks;
    }

    /**
     * Creates the component.
     *
     * @param duration game time until removal
     */
    public Lifetime(Duration duration) {
        this(Math.max(1L, duration.toMillis() * Gulp.engine().targetTps() / 1000L));
    }

    @Override
    protected void onTick() {
        remaining--;
        if (remaining <= 0) {
            entity().remove();
        }
    }

    /**
     * Returns the ticks left.
     *
     * @return ticks
     */
    public long remaining() {
        return remaining;
    }

    /**
     * Returns how much of the lifetime has passed.
     *
     * @return {@code 0..1}
     */
    public float progress() {
        return 1f - remaining / (float) total;
    }

    /** Starts the countdown again. */
    public void restart() {
        remaining = total;
    }
}
