package dev.gulp.api.entity;

import java.util.Set;

/**
 * The tags of an entity: short names used to group entities in queries.
 *
 * <pre>{@code
 * entity.tags().add("burning");
 * if (entity.tags().has("enemy")) { ... }
 * world.query().tag("enemy").forEach(Entity::remove);
 * }</pre>
 */
public interface Tags {

    /**
     * Adds a tag.
     *
     * @param tag the tag
     * @return {@code true} if it was not there
     */
    boolean add(String tag);

    /**
     * Removes a tag.
     *
     * @param tag the tag
     * @return {@code true} if it was there
     */
    boolean remove(String tag);

    /**
     * Returns whether a tag is present.
     *
     * @param tag the tag
     * @return {@code true} if present
     */
    boolean has(String tag);

    /**
     * Returns every tag.
     *
     * @return the tags, a snapshot
     */
    Set<String> all();
}
