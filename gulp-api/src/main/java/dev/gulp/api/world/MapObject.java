package dev.gulp.api.world;

import java.util.Map;

/**
 * An object placed in a Tiled object layer or an LDtk entity layer, in world units.
 *
 * <pre>{@code
 * WorldSource.ldtk(GameAssets.Maps.LEVELS).spawner((world, object) ->
 *         object.type().equals("Door") ? world.spawn(DOOR, object.x(), object.y()) : null);
 * }</pre>
 *
 * @param name the object name (Tiled) or identifier (LDtk)
 * @param type the class or type (Tiled) or identifier (LDtk)
 * @param layer the layer it was placed in
 * @param x centre, world units
 * @param y centre, world units
 * @param width world units
 * @param height world units
 * @param properties custom properties and fields, as text
 */
public record MapObject(
        String name,
        String type,
        String layer,
        float x,
        float y,
        float width,
        float height,
        Map<String, String> properties) {

    /**
     * Copies the properties.
     *
     * @param name name
     * @param type type
     * @param layer layer
     * @param x x
     * @param y y
     * @param width width
     * @param height height
     * @param properties properties
     */
    public MapObject {
        properties = Map.copyOf(properties);
    }
}
