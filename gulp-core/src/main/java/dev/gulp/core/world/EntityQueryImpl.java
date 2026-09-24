package dev.gulp.core.world;

import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityQuery;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * {@link EntityQuery}: picks the narrowest source (area in the grid, a component store, a tag set, or every entity)
 * and checks the remaining conditions on each candidate.
 */
final class EntityQueryImpl implements EntityQuery {

    private final WorldImpl world;
    private final List<Class<? extends Component>> with = new ArrayList<>(2);
    private final List<Class<? extends Component>> without = new ArrayList<>(1);
    private final List<String> tags = new ArrayList<>(2);
    private final List<String> withoutTags = new ArrayList<>(1);
    private final List<Predicate<Entity>> filters = new ArrayList<>(1);
    private @Nullable EntityType type;
    private @Nullable Rect area;
    private boolean near;
    private float nearX;
    private float nearY;
    private float radius;

    EntityQueryImpl(WorldImpl world) {
        this.world = world;
    }

    @Override
    public EntityQuery with(Class<? extends Component> componentType) {
        with.add(componentType);
        return this;
    }

    @Override
    public EntityQuery without(Class<? extends Component> componentType) {
        without.add(componentType);
        return this;
    }

    @Override
    public EntityQuery tag(String tag) {
        tags.add(tag);
        return this;
    }

    @Override
    public EntityQuery withoutTag(String tag) {
        withoutTags.add(tag);
        return this;
    }

    @Override
    public EntityQuery type(EntityType entityType) {
        this.type = entityType;
        return this;
    }

    @Override
    public EntityQuery near(Vec2 center, float distance) {
        near = true;
        nearX = center.x();
        nearY = center.y();
        radius = distance;
        return this;
    }

    @Override
    public EntityQuery in(Rect rect) {
        area = area == null ? rect : area.intersection(rect);
        return this;
    }

    @Override
    public EntityQuery filter(Predicate<Entity> test) {
        filters.add(test);
        return this;
    }

    private boolean matches(EntityImpl entity) {
        if (entity.removed || !entity.spawned) {
            return false;
        }
        if (type != null && !entity.type.equals(type)) {
            return false;
        }
        for (int i = 0; i < with.size(); i++) {
            if (!entity.has(with.get(i))) {
                return false;
            }
        }
        for (int i = 0; i < without.size(); i++) {
            if (entity.has(without.get(i))) {
                return false;
            }
        }
        for (int i = 0; i < tags.size(); i++) {
            if (!entity.tags.has(tags.get(i))) {
                return false;
            }
        }
        for (int i = 0; i < withoutTags.size(); i++) {
            if (entity.tags.has(withoutTags.get(i))) {
                return false;
            }
        }
        if (near) {
            float dx = entity.x - nearX;
            float dy = entity.y - nearY;
            if (dx * dx + dy * dy > radius * radius) {
                return false;
            }
        }
        Rect rect = area;
        if (rect != null) {
            float hw = entity.width / 2f;
            float hh = entity.height / 2f;
            if (entity.x + hw < rect.x()
                    || entity.x - hw > rect.x() + rect.width()
                    || entity.y + hh < rect.y()
                    || entity.y - hh > rect.y() + rect.height()) {
                return false;
            }
        }
        for (int i = 0; i < filters.size(); i++) {
            if (!filters.get(i).test(entity)) {
                return false;
            }
        }
        return true;
    }

    /** Visits every match; stops when the visitor returns false. */
    private void run(Predicate<EntityImpl> visitor) {
        Rect rect = area;
        if (near || rect != null) {
            float minX = near ? nearX - radius : -Float.MAX_VALUE;
            float minY = near ? nearY - radius : -Float.MAX_VALUE;
            float maxX = near ? nearX + radius : Float.MAX_VALUE;
            float maxY = near ? nearY + radius : Float.MAX_VALUE;
            if (rect != null) {
                minX = Math.max(minX, rect.x());
                minY = Math.max(minY, rect.y());
                maxX = Math.min(maxX, rect.x() + rect.width());
                maxY = Math.min(maxY, rect.y() + rect.height());
            }
            List<EntityImpl> found = new ArrayList<>();
            world.grid.query(minX, minY, maxX, maxY, found::add);
            // Keep spawn order so results do not depend on grid layout.
            found.sort(Comparator.comparingInt(e -> e.runtimeId));
            for (EntityImpl entity : found) {
                if (matches(entity) && !visitor.test(entity)) {
                    return;
                }
            }
            return;
        }
        List<EntityImpl> source = world.entities;
        ComponentStore smallest = null;
        for (Class<? extends Component> componentType : with) {
            ComponentStore store = world.store(componentType);
            if (store == null) {
                // No component of exactly this class: a superclass may match, so fall back to the other sources.
                continue;
            }
            if (smallest == null || store.size < smallest.size) {
                smallest = store;
            }
        }
        Set<EntityImpl> tagged = null;
        for (String tag : tags) {
            Set<EntityImpl> set = world.tagged(tag);
            if (tagged == null || set.size() < tagged.size()) {
                tagged = set;
            }
        }
        if (tagged != null && (smallest == null || tagged.size() <= smallest.size)) {
            for (EntityImpl entity : List.copyOf(tagged)) {
                if (matches(entity) && !visitor.test(entity)) {
                    return;
                }
            }
            return;
        }
        if (smallest != null) {
            List<EntityImpl> owners = new ArrayList<>(smallest.size);
            for (int i = 0; i < smallest.size; i++) {
                owners.add(smallest.owners[i]);
            }
            owners.sort(Comparator.comparingInt(e -> e.runtimeId));
            for (EntityImpl entity : owners) {
                if (matches(entity) && !visitor.test(entity)) {
                    return;
                }
            }
            return;
        }
        for (EntityImpl entity : List.copyOf(source)) {
            if (matches(entity) && !visitor.test(entity)) {
                return;
            }
        }
    }

    @Override
    public void forEach(Consumer<Entity> action) {
        run(entity -> {
            action.accept(entity);
            return true;
        });
    }

    @Override
    public @Nullable Entity first() {
        Entity[] result = new Entity[1];
        run(entity -> {
            result[0] = entity;
            return false;
        });
        return result[0];
    }

    @Override
    public int count() {
        int[] count = new int[1];
        run(entity -> {
            count[0]++;
            return true;
        });
        return count[0];
    }

    @Override
    public List<Entity> list() {
        List<Entity> result = new ArrayList<>();
        run(entity -> {
            result.add(entity);
            return true;
        });
        return result;
    }

    @Override
    public List<Entity> sortedByDistance(Vec2 point) {
        List<Entity> result = list();
        float px = point.x();
        float py = point.y();
        result.sort(Comparator.comparingDouble(e -> {
            float dx = e.x() - px;
            float dy = e.y() - py;
            return dx * dx + dy * dy;
        }));
        return result;
    }
}
