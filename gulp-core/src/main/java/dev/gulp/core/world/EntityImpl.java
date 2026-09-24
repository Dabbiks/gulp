package dev.gulp.core.world;

import dev.gulp.api.Logger;
import dev.gulp.api.PauseMode;
import dev.gulp.api.data.Config;
import dev.gulp.api.data.DataContainer;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityTeleportEvent;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.entity.Tags;
import dev.gulp.api.event.Event;
import dev.gulp.api.event.EventPriority;
import dev.gulp.api.event.Subscription;
import dev.gulp.api.event.TargetedEvent;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.scheduler.Scheduler;
import dev.gulp.api.spi.ComponentAccess;
import dev.gulp.api.world.Location;
import dev.gulp.api.world.World;
import dev.gulp.core.data.DataContainerImpl;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** {@link Entity}: transform, look, components in slots of their stores, tags, data, hierarchy. */
final class EntityImpl implements Entity {

    WorldImpl world;
    final UUID id;
    final int runtimeId;
    final EntityType type;
    final EntityOwner owner = new EntityOwner();
    private @Nullable String name;

    float x;
    float y;
    float prevX;
    float prevY;
    float rotation;
    float prevRotation;
    float scaleX = 1f;
    float scaleY = 1f;
    float width;
    float height;
    String layer;
    int zIndex;
    boolean visible = true;
    Color tint = Color.WHITE;
    boolean flipX;
    boolean flipY;

    private @Nullable EntityImpl parent;
    private @Nullable List<EntityImpl> children;
    private float attachX;
    private float attachY;
    private boolean detachOnRemove;

    Component[] components = new Component[4];
    int[] slots = new int[4];
    int componentCount;

    final TagsImpl tags = new TagsImpl();
    private @Nullable DataContainer data;
    private boolean persistent;
    PauseMode pauseMode;

    boolean spawned;
    boolean removed;
    boolean onScreen;
    int screenStamp;
    boolean hovered;

    int cellMinX;
    int cellMinY;
    int cellMaxX;
    int cellMaxY;
    boolean inGrid;
    int gridStamp;
    long chunkKey = Long.MIN_VALUE;

    EntityImpl(WorldImpl world, EntityType type, UUID id, int runtimeId, float x, float y) {
        this.world = world;
        this.type = type;
        this.id = id;
        this.runtimeId = runtimeId;
        this.x = x;
        this.y = y;
        this.prevX = x;
        this.prevY = y;
        this.width = type.size().x();
        this.height = type.size().y();
        this.layer = type.layer();
        this.zIndex = type.zIndex();
        this.persistent = type.isPersistent();
        this.pauseMode = type.pauseMode();
        for (String tag : type.tags()) {
            tags.tags.add(tag);
        }
    }

    // ------------------------------------------------------------------ identity

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public int runtimeId() {
        return runtimeId;
    }

    @Override
    public EntityType type() {
        return type;
    }

    @Override
    public World world() {
        return world;
    }

    @Override
    public @Nullable String name() {
        return name;
    }

    @Override
    public Entity setName(@Nullable String newName) {
        if (spawned) {
            world.rename(this, name, newName);
        }
        name = newName;
        return this;
    }

    // ------------------------------------------------------------------ transform

    @Override
    public Vec2 position() {
        return new Vec2(x, y);
    }

    @Override
    public float x() {
        return x;
    }

    @Override
    public float y() {
        return y;
    }

    @Override
    public Entity setPosition(float newX, float newY) {
        float dx = newX - x;
        float dy = newY - y;
        x = newX;
        y = newY;
        if (spawned) {
            world.grid.moved(this);
        }
        List<EntityImpl> kids = children;
        if (kids != null) {
            for (int i = 0; i < kids.size(); i++) {
                EntityImpl child = kids.get(i);
                child.setPosition(child.x + dx, child.y + dy);
            }
        }
        return this;
    }

    @Override
    public Entity setPosition(Vec2 position) {
        return setPosition(position.x(), position.y());
    }

    @Override
    public Entity teleport(Location location) {
        World target = location.world();
        if (target == world) {
            return teleportWithin(location.x(), location.y(), location);
        }
        if (!(target instanceof WorldImpl other)) {
            throw new IllegalArgumentException("Unknown world " + target);
        }
        if (world.worlds.events.hasListeners(EntityTeleportEvent.class)) {
            EntityTeleportEvent event =
                    world.worlds.events.call(new EntityTeleportEvent(this, new Location(world, x, y), location));
            if (event.isCancelled()) {
                return this;
            }
        }
        detach();
        world.transfer(this, other);
        snap(location.x(), location.y());
        return this;
    }

    @Override
    public Entity teleport(float newX, float newY) {
        return teleportWithin(newX, newY, null);
    }

    private Entity teleportWithin(float newX, float newY, @Nullable Location target) {
        if (spawned && world.worlds.events.hasListeners(EntityTeleportEvent.class)) {
            Location to = target != null ? target : new Location(world, newX, newY);
            EntityTeleportEvent event =
                    world.worlds.events.call(new EntityTeleportEvent(this, new Location(world, x, y), to));
            if (event.isCancelled()) {
                return this;
            }
        }
        snap(newX, newY);
        return this;
    }

    /** Moves without interpolation, children included. */
    void snap(float newX, float newY) {
        float dx = newX - x;
        float dy = newY - y;
        x = newX;
        y = newY;
        prevX = newX;
        prevY = newY;
        if (spawned) {
            world.grid.moved(this);
        }
        List<EntityImpl> kids = children;
        if (kids != null) {
            for (int i = 0; i < kids.size(); i++) {
                EntityImpl child = kids.get(i);
                child.snap(child.x + dx, child.y + dy);
            }
        }
    }

    float renderX(float alpha) {
        return prevX + (x - prevX) * alpha;
    }

    float renderY(float alpha) {
        return prevY + (y - prevY) * alpha;
    }

    float renderRotation(float alpha) {
        return prevRotation + (rotation - prevRotation) * alpha;
    }

    void rememberPrevious() {
        prevX = x;
        prevY = y;
        prevRotation = rotation;
    }

    @Override
    public float rotation() {
        return rotation;
    }

    @Override
    public Entity setRotation(float degrees) {
        rotation = degrees;
        return this;
    }

    @Override
    public Vec2 scale() {
        return new Vec2(scaleX, scaleY);
    }

    @Override
    public Entity setScale(float sx, float sy) {
        scaleX = sx;
        scaleY = sy;
        return this;
    }

    @Override
    public Vec2 size() {
        return new Vec2(width, height);
    }

    @Override
    public Entity setSize(float newWidth, float newHeight) {
        if (!(newWidth >= 0f && newHeight >= 0f)) {
            throw new IllegalArgumentException("Entity size must not be negative: " + newWidth + " x " + newHeight);
        }
        width = newWidth;
        height = newHeight;
        if (spawned) {
            world.grid.moved(this);
        }
        return this;
    }

    @Override
    public Rect bounds() {
        return new Rect(x - width / 2f, y - height / 2f, width, height);
    }

    // ------------------------------------------------------------------ look

    @Override
    public String layer() {
        return layer;
    }

    @Override
    public Entity setLayer(String newLayer) {
        layer = newLayer;
        return this;
    }

    @Override
    public int zIndex() {
        return zIndex;
    }

    @Override
    public Entity setZIndex(int newZIndex) {
        zIndex = newZIndex;
        return this;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public Entity setVisible(boolean newVisible) {
        visible = newVisible;
        return this;
    }

    @Override
    public Color tint() {
        return tint;
    }

    @Override
    public Entity setTint(Color newTint) {
        tint = newTint;
        return this;
    }

    @Override
    public boolean isFlipX() {
        return flipX;
    }

    @Override
    public Entity setFlipX(boolean flip) {
        flipX = flip;
        return this;
    }

    @Override
    public boolean isFlipY() {
        return flipY;
    }

    @Override
    public Entity setFlipY(boolean flip) {
        flipY = flip;
        return this;
    }

    // ------------------------------------------------------------------ hierarchy

    @Override
    public Entity attach(Entity child, Vec2 offset) {
        if (!(child instanceof EntityImpl kid) || kid.world != world) {
            throw new IllegalArgumentException("Only entities of the same world can be attached");
        }
        for (EntityImpl ancestor = this; ancestor != null; ancestor = ancestor.parent) {
            if (ancestor == kid) {
                throw new IllegalArgumentException("An entity cannot be attached to itself or its descendant");
            }
        }
        kid.detach();
        kid.parent = this;
        kid.attachX = offset.x();
        kid.attachY = offset.y();
        List<EntityImpl> kids = children;
        if (kids == null) {
            kids = new ArrayList<>(2);
            children = kids;
        }
        kids.add(kid);
        kid.snap(x + offset.x(), y + offset.y());
        return this;
    }

    @Override
    public Entity detach() {
        EntityImpl current = parent;
        if (current != null && current.children != null) {
            current.children.remove(this);
        }
        parent = null;
        return this;
    }

    @Override
    public @Nullable Entity parent() {
        return parent;
    }

    @Override
    public List<Entity> children() {
        return children == null ? List.of() : List.copyOf(children);
    }

    @Nullable List<EntityImpl> childList() {
        return children;
    }

    @Override
    public boolean isDetachOnRemove() {
        return detachOnRemove;
    }

    @Override
    public Entity setDetachOnRemove(boolean detach) {
        detachOnRemove = detach;
        return this;
    }

    // ------------------------------------------------------------------ components

    private int indexOf(Class<?> type) {
        for (int i = 0; i < componentCount; i++) {
            if (components[i].getClass() == type) {
                return i;
            }
        }
        for (int i = 0; i < componentCount; i++) {
            if (type.isInstance(components[i])) {
                return i;
            }
        }
        return -1;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C extends Component> C get(Class<C> type) {
        int index = indexOf(type);
        if (index < 0) {
            throw new IllegalStateException(
                    "Entity " + this.type.key() + " has no " + type.getSimpleName() + " component");
        }
        return (C) components[index];
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C extends Component> Optional<C> find(Class<C> type) {
        int index = indexOf(type);
        return index < 0 ? Optional.empty() : Optional.of((C) components[index]);
    }

    /** Like {@link #find} without allocating. */
    @SuppressWarnings("unchecked")
    <C extends Component> @Nullable C component(Class<C> type) {
        for (int i = 0; i < componentCount; i++) {
            if (components[i].getClass() == type) {
                return (C) components[i];
            }
        }
        return null;
    }

    @Override
    public boolean has(Class<? extends Component> type) {
        return indexOf(type) >= 0;
    }

    @Override
    public <C extends Component> C add(C component) {
        if (component.isAttached()) {
            throw new IllegalArgumentException(component.getClass().getSimpleName() + " is attached to an entity");
        }
        for (int i = 0; i < componentCount; i++) {
            if (components[i].getClass() == component.getClass()) {
                throw new IllegalArgumentException("Entity " + type.key() + " already has a "
                        + component.getClass().getSimpleName());
            }
        }
        if (componentCount == components.length) {
            components = Arrays.copyOf(components, componentCount * 2);
            slots = Arrays.copyOf(slots, componentCount * 2);
        }
        components[componentCount] = component;
        slots[componentCount] = -1;
        componentCount++;
        ComponentAccess.bind(component, this);
        ComponentAccess.attach(component);
        if (spawned) {
            world.storeAdd(this, component);
            ComponentAccess.spawn(component);
        }
        return component;
    }

    @Override
    public boolean remove(Class<? extends Component> type) {
        int index = indexOf(type);
        if (index < 0) {
            return false;
        }
        Component component = components[index];
        int slot = slots[index];
        System.arraycopy(components, index + 1, components, index, componentCount - index - 1);
        System.arraycopy(slots, index + 1, slots, index, componentCount - index - 1);
        componentCount--;
        components[componentCount] = null;
        try {
            ComponentAccess.remove(component);
        } finally {
            ComponentAccess.bind(component, null);
            if (spawned && slot >= 0) {
                world.storeRemove(component, slot);
            }
        }
        return true;
    }

    void setSlot(Component component, int slot) {
        for (int i = 0; i < componentCount; i++) {
            if (components[i] == component) {
                slots[i] = slot;
                return;
            }
        }
    }

    @Override
    public List<Component> components() {
        return List.of(Arrays.copyOf(components, componentCount));
    }

    // ------------------------------------------------------------------ tags, data, flags

    @Override
    public Tags tags() {
        return tags;
    }

    @Override
    public DataContainer data() {
        DataContainer current = data;
        if (current == null) {
            current = new DataContainerImpl(JsonObject.EMPTY);
            data = current;
        }
        return current;
    }

    @Override
    public boolean isPersistent() {
        return persistent;
    }

    @Override
    public Entity setPersistent(boolean value) {
        persistent = value;
        return this;
    }

    @Override
    public PauseMode pauseMode() {
        return pauseMode;
    }

    @Override
    public Entity setPauseMode(PauseMode mode) {
        pauseMode = mode;
        return this;
    }

    @Override
    public <E extends Event & TargetedEvent> Subscription on(Class<E> type, Consumer<? super E> handler) {
        return world.worlds.events.on(type, this, EventPriority.NORMAL, handler, owner);
    }

    @Override
    public Scheduler scheduler() {
        return world.worlds.scheduler.api().owner(owner);
    }

    @Override
    public void remove() {
        if (removed) {
            return;
        }
        removed = true;
        world.queueRemoval(this);
        List<EntityImpl> kids = children;
        if (kids != null) {
            for (EntityImpl child : List.copyOf(kids)) {
                if (detachOnRemove) {
                    child.detach();
                } else {
                    child.remove();
                }
            }
        }
    }

    @Override
    public boolean isRemoved() {
        return removed;
    }

    @Override
    public boolean isSpawned() {
        return spawned && !removed;
    }

    @Override
    public boolean isOnScreen() {
        return onScreen;
    }

    @Override
    public String toString() {
        return "Entity[" + type.key() + " #" + runtimeId + (name != null ? " '" + name + "'" : "") + " at " + x + ", "
                + y + "]";
    }

    /** Tags that keep the world index up to date. */
    final class TagsImpl implements Tags {
        final Set<String> tags = new LinkedHashSet<>();

        @Override
        public boolean add(String tag) {
            boolean added = tags.add(tag);
            if (added && spawned) {
                world.tagAdded(EntityImpl.this, tag);
            }
            return added;
        }

        @Override
        public boolean remove(String tag) {
            boolean removedTag = tags.remove(tag);
            if (removedTag && spawned) {
                world.tagRemoved(EntityImpl.this, tag);
            }
            return removedTag;
        }

        @Override
        public boolean has(String tag) {
            return tags.contains(tag);
        }

        @Override
        public Set<String> all() {
            return Collections.unmodifiableSet(new LinkedHashSet<>(tags));
        }

        @Override
        public String toString() {
            return tags.toString();
        }
    }

    /** Owns the entity's handlers and tasks, which end with it. */
    final class EntityOwner implements dev.gulp.core.ScopedOwner {
        @Override
        public String id() {
            return "entity:" + runtimeId;
        }

        @Override
        public boolean isEnabled() {
            return !removed || !spawned;
        }

        @Override
        public Logger logger() {
            return world.worlds.logger;
        }

        @Override
        public Config config() {
            return world.worlds.engine.game().config();
        }

        @Override
        public dev.gulp.api.Engine engine() {
            return world.worlds.engine;
        }

        EntityImpl entity() {
            return EntityImpl.this;
        }
    }
}
