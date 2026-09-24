package dev.gulp.core.world;

import dev.gulp.api.event.Events;
import dev.gulp.api.world.TileOrientation;
import dev.gulp.api.world.TileShape;
import dev.gulp.api.world.TileType;
import dev.gulp.core.physics.PhysicsHost;
import java.util.List;

/** Gives the physics of a world its collision tiles: solid tiles of collision layers, on orthogonal maps only. */
final class WorldPhysicsHost implements PhysicsHost {

    private final WorldImpl world;

    WorldPhysicsHost(WorldImpl world) {
        this.world = world;
    }

    @Override
    public void tiles(int minX, int minY, int maxX, int maxY, TileVisitor visitor) {
        TileMapImpl map = world.tileMap;
        if (map.orientation() != TileOrientation.ORTHOGONAL || map.chunks.size() == 0) {
            return;
        }
        List<TileMapImpl.TileLayerImpl> layers = map.layers;
        for (int i = 0; i < layers.size(); i++) {
            TileMapImpl.TileLayerImpl layer = layers.get(i);
            if (!layer.isCollision()) {
                continue;
            }
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    int cell = map.cell(layer, x, y);
                    if (cell == 0) {
                        continue;
                    }
                    TileType type = map.typeOf(cell);
                    if (type != null && type.shape().isSolid()) {
                        visitor.visit(x, y, type, cell & ~ChunkImpl.ID_MASK);
                    }
                }
            }
        }
    }

    @Override
    public boolean isFullTile(int x, int y) {
        TileMapImpl map = world.tileMap;
        List<TileMapImpl.TileLayerImpl> layers = map.layers;
        for (int i = 0; i < layers.size(); i++) {
            TileMapImpl.TileLayerImpl layer = layers.get(i);
            if (layer.isCollision()) {
                TileType type = map.typeOf(map.cell(layer, x, y));
                if (type != null && type.shape() == TileShape.FULL) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public Events events() {
        return world.worlds.events;
    }

    @Override
    public float tickSeconds() {
        return 1f / world.worlds.engine.targetTps();
    }
}
