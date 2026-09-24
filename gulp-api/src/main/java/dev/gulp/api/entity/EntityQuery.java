package dev.gulp.api.entity;

import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * A search for entities in one world. Conditions are combined with "and"; the engine starts from the narrowest index
 * (spatial grid, component store or tag) and filters the rest. Removed entities are never returned.
 *
 * <pre>{@code
 * world.query().with(Health.class).tag("enemy").near(player.position(), 8)
 *         .forEach(e -> e.get(Health.class).damage(2));
 * Entity closest = world.query().tag("coin").sortedByDistance(player.position()).stream().findFirst().orElse(null);
 * }</pre>
 */
public interface EntityQuery {

    /**
     * Keeps entities with a component.
     *
     * @param type the component class
     * @return this query
     */
    EntityQuery with(Class<? extends Component> type);

    /**
     * Keeps entities without a component.
     *
     * @param type the component class
     * @return this query
     */
    EntityQuery without(Class<? extends Component> type);

    /**
     * Keeps entities with a tag.
     *
     * @param tag the tag
     * @return this query
     */
    EntityQuery tag(String tag);

    /**
     * Keeps entities without a tag.
     *
     * @param tag the tag
     * @return this query
     */
    EntityQuery withoutTag(String tag);

    /**
     * Keeps entities of a type.
     *
     * @param type the type
     * @return this query
     */
    EntityQuery type(EntityType type);

    /**
     * Keeps entities whose position is within a distance.
     *
     * @param center the centre
     * @param radius world units
     * @return this query
     */
    EntityQuery near(Vec2 center, float radius);

    /**
     * Keeps entities whose bounds overlap a rectangle.
     *
     * @param area world units
     * @return this query
     */
    EntityQuery in(Rect area);

    /**
     * Keeps entities that pass a test.
     *
     * @param test the test
     * @return this query
     */
    EntityQuery filter(Predicate<Entity> test);

    /**
     * Runs an action for every match.
     *
     * @param action the action
     */
    void forEach(Consumer<Entity> action);

    /**
     * Returns the first match.
     *
     * @return the entity, or {@code null}
     */
    @Nullable Entity first();

    /**
     * Counts the matches.
     *
     * @return the count
     */
    int count();

    /**
     * Returns the matches.
     *
     * @return the entities
     */
    List<Entity> list();

    /**
     * Returns the matches, nearest first.
     *
     * @param point the point to measure from
     * @return the entities
     */
    List<Entity> sortedByDistance(Vec2 point);
}
