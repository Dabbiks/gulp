package dev.gulp.api.world;

import dev.gulp.api.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Turns map objects into entities. {@link WorldSource#spawn(String, dev.gulp.api.entity.EntityType)} covers the common
 * case; a spawner handles the rest.
 *
 * <pre>{@code
 * ObjectSpawner spawner = (world, object) -> switch (object.type()) {
 *     case "Coin" -> world.spawn(COIN, object.x(), object.y());
 *     default -> null;
 * };
 * }</pre>
 */
@FunctionalInterface
public interface ObjectSpawner {

    /**
     * Spawns an entity for a map object.
     *
     * @param world the world being loaded
     * @param object the object
     * @return the entity, or {@code null} to skip the object; its properties are copied into the entity data
     */
    @Nullable Entity spawn(World world, MapObject object);
}
