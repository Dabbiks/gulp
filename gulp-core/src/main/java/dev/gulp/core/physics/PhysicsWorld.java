package dev.gulp.core.physics;

import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.math.GridPos;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.physics.Body;
import dev.gulp.api.physics.BodyType;
import dev.gulp.api.physics.Collider;
import dev.gulp.api.physics.CollisionLayer;
import dev.gulp.api.physics.CollisionMask;
import dev.gulp.api.physics.DistanceJoint;
import dev.gulp.api.physics.EntityCollideEndEvent;
import dev.gulp.api.physics.EntityCollideEvent;
import dev.gulp.api.physics.EntityLandEvent;
import dev.gulp.api.physics.Joint;
import dev.gulp.api.physics.MotorJoint;
import dev.gulp.api.physics.MouseJoint;
import dev.gulp.api.physics.Mover;
import dev.gulp.api.physics.Physics;
import dev.gulp.api.physics.PreCollideEvent;
import dev.gulp.api.physics.PrismaticJoint;
import dev.gulp.api.physics.RayHit;
import dev.gulp.api.physics.RevoluteJoint;
import dev.gulp.api.physics.RopeJoint;
import dev.gulp.api.physics.Shape;
import dev.gulp.api.physics.Trigger;
import dev.gulp.api.physics.TriggerEnterEvent;
import dev.gulp.api.physics.TriggerExitEvent;
import dev.gulp.api.physics.WeldJoint;
import dev.gulp.api.physics.WheelJoint;
import dev.gulp.api.render.Draw;
import dev.gulp.api.spi.PhysicsAccess;
import dev.gulp.api.world.TileShape;
import dev.gulp.api.world.TileType;
import dev.gulp.core.util.LongObjectMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The physics of one world: proxies of physical entities in a broad phase, kinematic movers, rigid bodies solved with
 * soft sequential impulses in substeps, joints, triggers, queries against entities and collision tiles, and debug
 * drawing. {@link #step()} runs once per unpaused tick after the entity components.
 */
public final class PhysicsWorld implements Physics {

    private static final float LINEAR_SLEEP = 0.05f;
    private static final float ANGULAR_SLEEP = (float) Math.toRadians(2);
    private static final float TIME_TO_SLEEP = 0.5f;
    private static final float CONTACT_HERTZ = 30f;
    private static final float CONTACT_DAMPING = 10f;
    private static final float MAX_PUSH = 3f;
    private static final float RESTITUTION_THRESHOLD = 1f;
    private static final long TILE_FLAG = 1L << 62;

    final PhysicsHost host;
    final Broadphase broadphase = new Broadphase();
    private final Map<Entity, Proxy> proxies = new IdentityHashMap<>();
    private final LongObjectMap<Proxy> bySerial = new LongObjectMap<>();
    private final List<Proxy> proxyList = new ArrayList<>();
    private final List<BodySim> bodies = new ArrayList<>();
    private final List<TriggerState> triggers = new ArrayList<>();
    private final Map<Trigger, TriggerState> triggerByComponent = new IdentityHashMap<>();
    private final List<Joints.JointImpl> joints = new ArrayList<>();
    private final Map<ContactKey, ContactConstraint> contacts = new HashMap<>();
    private final List<ContactConstraint> active = new ArrayList<>();
    private final Map<TileShape, List<@Nullable List<Convex>>> tileCache = new IdentityHashMap<>();
    private final MoverSolver movers = new MoverSolver(this);
    private final int[] matrix = new int[CollisionLayer.MAX];
    private Vec2 gravity;
    private int subSteps = 4;

    private final ContactKey probe = new ContactKey();
    private final List<Proxy> candidates = new ArrayList<>();
    private final Consumer<Proxy> collect = candidates::add;
    private final List<Placed> placedA = new ArrayList<>();
    private final Placed placedB = new Placed();
    private final Placed scratch = new Placed();
    private final Manifold manifold = new Manifold();
    private final Joints.Softness contactSoft = new Joints.Softness();

    /**
     * Creates the physics of a world.
     *
     * @param host tile and event access
     * @param gravity the starting gravity
     */
    public PhysicsWorld(PhysicsHost host, Vec2 gravity) {
        this.host = host;
        this.gravity = gravity;
        java.util.Arrays.fill(matrix, -1);
    }

    // ------------------------------------------------------------------ keys

    static long tileKey(int x, int y) {
        return TILE_FLAG | ((long) (x & 0x7FFFFFF) << 27) | (y & 0x7FFFFFF);
    }

    static boolean isTileKey(long key) {
        return (key & TILE_FLAG) != 0;
    }

    static int tileX(long key) {
        return ((int) ((key >> 27) & 0x7FFFFFF)) << 5 >> 5;
    }

    static int tileY(long key) {
        return ((int) (key & 0x7FFFFFF)) << 5 >> 5;
    }

    @Nullable Proxy proxyBySerial(int serial) {
        return bySerial.get(serial);
    }

    // ------------------------------------------------------------------ components

    /**
     * Starts simulating a component; see {@link PhysicsAccess.Backend#attach}.
     *
     * @param component a collider, mover or trigger
     */
    public void attach(Component component) {
        Entity entity = component.entity();
        switch (component) {
            case Collider collider -> proxyOf(entity).collider = collider;
            case Mover mover -> proxyOf(entity).mover = mover;
            case Trigger trigger -> {
                TriggerState state = new TriggerState(trigger);
                triggers.add(state);
                triggerByComponent.put(trigger, state);
            }
            default -> {
                return;
            }
        }
        Proxy proxy = proxies.get(entity);
        if (proxy != null) {
            proxy.refresh();
            proxy.updateBounds(0.1f);
            broadphase.moved(proxy);
        }
    }

    /**
     * Creates the simulation of a body.
     *
     * @param body the body, spawned
     * @return its live state
     */
    public PhysicsAccess.BodyHandle attachBody(Body body) {
        Proxy proxy = proxyOf(body.entity());
        BodySim sim = new BodySim(this, body, proxy);
        proxy.body = sim;
        proxy.refresh();
        sim.updateMass();
        proxy.updateBounds(0.1f);
        broadphase.moved(proxy);
        bodies.add(sim);
        return sim;
    }

    /**
     * Stops simulating a component.
     *
     * @param component the component
     */
    public void detach(Component component) {
        if (component instanceof Trigger trigger) {
            TriggerState state = triggerByComponent.remove(trigger);
            if (state != null) {
                triggers.remove(state);
            }
            return;
        }
        Entity entity = component.isAttached() ? component.entity() : null;
        Proxy proxy = null;
        if (entity != null) {
            proxy = proxies.get(entity);
        } else {
            for (Proxy candidate : proxyList) {
                if (candidate.collider == component
                        || candidate.mover == component
                        || (candidate.body != null && candidate.body.body == component)) {
                    proxy = candidate;
                    break;
                }
            }
        }
        if (proxy == null) {
            return;
        }
        if (proxy.collider == component) {
            proxy.collider = null;
        } else if (proxy.mover == component) {
            proxy.mover = null;
        } else if (proxy.body != null && proxy.body.body == component) {
            BodySim sim = proxy.body;
            bodies.remove(sim);
            proxy.body = null;
            dropContacts(sim);
            for (int i = joints.size() - 1; i >= 0; i--) {
                Joints.JointImpl joint = joints.get(i);
                if (joint.a == sim || joint.b == sim) {
                    removeJoint(joint);
                }
            }
        }
        if (proxy.isEmpty()) {
            broadphase.remove(proxy);
            proxies.remove(proxy.entity);
            bySerial.remove(proxy.serial);
            proxyList.remove(proxy);
            for (Iterator<ContactConstraint> it = contacts.values().iterator(); it.hasNext(); ) {
                if (it.next().other == proxy) {
                    it.remove();
                }
            }
        } else {
            proxy.refresh();
        }
    }

    private Proxy proxyOf(Entity entity) {
        Proxy proxy = proxies.get(entity);
        if (proxy == null) {
            proxy = new Proxy(entity, entity.runtimeId());
            proxies.put(entity, proxy);
            bySerial.put(proxy.serial, proxy);
            proxyList.add(proxy);
        }
        return proxy;
    }

    private void dropContacts(BodySim sim) {
        contacts.values().removeIf(c -> c.a == sim || c.b == sim);
    }

    void bodyChanged(BodySim sim) {
        dropContacts(sim);
    }

    /**
     * Moves a mover; see {@link PhysicsAccess.Backend#moveAndSlide}.
     *
     * @param mover the mover
     * @param vx x velocity
     * @param vy y velocity
     * @param out the result
     */
    public void moveAndSlide(Mover mover, float vx, float vy, PhysicsAccess.MoveResult out) {
        Proxy proxy = proxies.get(mover.entity());
        if (proxy == null) {
            // Not spawned yet: just move.
            float dt = host.tickSeconds();
            mover.entity()
                    .setPosition(mover.entity().x() + vx * dt, mover.entity().y() + vy * dt);
            out.contacts.clear();
            out.velocityX = vx;
            out.velocityY = vy;
            out.floor = false;
            out.wall = false;
            out.ceiling = false;
            out.floorEntity = null;
            return;
        }
        movers.move(proxy, mover, vx, vy, out);
    }

    /**
     * Returns the entities inside a trigger.
     *
     * @param trigger the trigger
     * @return the entities
     */
    public List<Entity> triggered(Trigger trigger) {
        TriggerState state = triggerByComponent.get(trigger);
        return state == null ? List.of() : Collections.unmodifiableList(state.inside);
    }

    // ------------------------------------------------------------------ filtering

    boolean canTouch(Proxy a, Proxy b) {
        return (a.maskBits & b.layerBit) != 0 && (b.maskBits & a.layerBit) != 0 && matrixAllows(a.layer, b.layer);
    }

    boolean canTouchTiles(Proxy proxy) {
        int tiles = tilesBit();
        return (proxy.maskBits & tiles) != 0 && matrixAllows(proxy.layer, CollisionLayer.TILES);
    }

    private static int tilesBit() {
        int bit = CollisionLayer.TILES.bit();
        return bit >= 0 ? 1 << bit : 0;
    }

    private boolean matrixAllows(CollisionLayer a, CollisionLayer b) {
        int ba = a.bit();
        int bb = b.bit();
        if (ba < 0 || bb < 0) {
            return true;
        }
        return (matrix[ba] & (1 << bb)) != 0;
    }

    private static boolean inMask(CollisionMask mask, int layerBit) {
        return (mask.bits() & layerBit) != 0;
    }

    // ------------------------------------------------------------------ tiles

    List<Convex> tilePieces(TileType type, int flags) {
        TileShape shape = type.shape();
        List<@Nullable List<Convex>> variants = tileCache.get(shape);
        if (variants == null) {
            variants = new ArrayList<>(Collections.nCopies(8, null));
            tileCache.put(shape, variants);
        }
        int variant = (flags >>> 29) & 7;
        List<Convex> pieces = variants.get(variant);
        if (pieces == null) {
            pieces = new ArrayList<>();
            dev.gulp.api.math.Polygon polygon = shape.polygon();
            if (polygon != null) {
                List<Vec2> points = new ArrayList<>();
                for (Vec2 p : polygon.vertices()) {
                    float px = p.x();
                    float py = p.y();
                    if ((variant & 1) != 0) {
                        float t = px;
                        px = py;
                        py = t;
                    }
                    if ((variant & 4) != 0) {
                        px = 1f - px;
                    }
                    if ((variant & 2) != 0) {
                        py = 1f - py;
                    }
                    points.add(new Vec2(px, py));
                }
                for (List<Vec2> part : Shapes.convexParts(points)) {
                    pieces.add(Shapes.polygonPiece(part, 0f, 0f));
                }
            }
            variants.set(variant, pieces);
        }
        return pieces;
    }

    private boolean internalTileEdge(int tx, int ty, float nx, float ny) {
        if (!host.isFullTile(tx, ty)) {
            return false;
        }
        if (nx < -0.7f) {
            return host.isFullTile(tx - 1, ty);
        }
        if (nx > 0.7f) {
            return host.isFullTile(tx + 1, ty);
        }
        if (ny < -0.7f) {
            return host.isFullTile(tx, ty - 1);
        }
        if (ny > 0.7f) {
            return host.isFullTile(tx, ty + 1);
        }
        return false;
    }

    // ------------------------------------------------------------------ step

    /** Runs one tick: refreshes proxies, simulates bodies, reports contacts, sleeps idle bodies and updates triggers. */
    public void step() {
        float dt = host.tickSeconds();
        for (int i = 0; i < proxyList.size(); i++) {
            Proxy proxy = proxyList.get(i);
            proxy.refresh();
            BodySim body = proxy.body;
            if (body != null) {
                if (body.massChanged) {
                    body.updateMass();
                } else if (body.movedByGame()) {
                    body.syncFromEntity();
                    body.wake();
                }
            }
            float margin = 0.1f;
            if (body != null && body.type != BodyType.STATIC) {
                margin += (Math.abs(body.vx) + Math.abs(body.vy)) * dt;
            }
            proxy.updateBounds(margin);
            broadphase.moved(proxy);
            // Anything that moves on its own (kinematic bodies, colliders moved by game code, movers) wakes the
            // sleeping bodies it reaches; sleeping bodies make no contacts of their own.
            boolean moving =
                    body != null && body.type == BodyType.KINEMATIC && (body.vx != 0f || body.vy != 0f || body.w != 0f);
            boolean moved = (body == null || body.type != BodyType.DYNAMIC)
                    && (proxy.entity.x() != proxy.lastX || proxy.entity.y() != proxy.lastY);
            if ((moving || moved) && !bodies.isEmpty()) {
                wakeAround(proxy, body != null ? (Math.abs(body.vx) + Math.abs(body.vy)) * dt : 0f);
            }
            proxy.lastX = proxy.entity.x();
            proxy.lastY = proxy.entity.y();
        }
        if (!bodies.isEmpty()) {
            updateContacts(dt);
            solve(dt);
            reportContacts();
            sleep(dt);
        }
        updateTriggers();
    }

    private final List<Proxy> nearby = new ArrayList<>();

    private void wakeAround(Proxy proxy, float reach) {
        nearby.clear();
        float m = Collide.SPECULATIVE + reach;
        broadphase.query(proxy.minX - m, proxy.minY - m, proxy.maxX + m, proxy.maxY + m, nearby::add);
        for (int i = 0; i < nearby.size(); i++) {
            BodySim other = nearby.get(i).body;
            if (other != null && other.isDynamic() && !other.awake && nearby.get(i) != proxy) {
                other.wake();
            }
        }
    }

    private void updateContacts(float dt) {
        for (ContactConstraint c : contacts.values()) {
            c.seen = false;
            c.wasTouching = c.touching;
            c.disabled = false;
        }
        for (int i = 0; i < bodies.size(); i++) {
            BodySim body = bodies.get(i);
            if (!body.isDynamic() || !body.awake) {
                continue;
            }
            Proxy self = body.proxy;
            ensurePlaced(self.pieces.size());
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;
            for (int p = 0; p < self.pieces.size(); p++) {
                Placed placed = self.place(p, placedA.get(p));
                minX = Math.min(minX, placed.minX);
                minY = Math.min(minY, placed.minY);
                maxX = Math.max(maxX, placed.maxX);
                maxY = Math.max(maxY, placed.maxY);
            }
            float speed = (Math.abs(body.vx) + Math.abs(body.vy)) * dt;
            float margin = Collide.SPECULATIVE + Math.min(speed, 2f);
            candidates.clear();
            broadphase.query(minX - margin, minY - margin, maxX + margin, maxY + margin, collect);
            for (int c = 0; c < candidates.size(); c++) {
                Proxy other = candidates.get(c);
                if (other == self || other.pieces.isEmpty() || !canTouch(self, other)) {
                    continue;
                }
                BodySim otherBody = other.body;
                if (otherBody != null && otherBody.isDynamic() && otherBody.awake && other.serial < self.serial) {
                    continue;
                }
                if (jointed(body, otherBody)) {
                    continue;
                }
                for (int p = 0; p < self.pieces.size(); p++) {
                    Placed a = placedA.get(p);
                    for (int q = 0; q < other.pieces.size(); q++) {
                        Placed b = other.place(q, placedB);
                        if (!b.overlapsBox(a.minX - margin, a.minY - margin, a.maxX + margin, a.maxY + margin)) {
                            continue;
                        }
                        Collide.collide(a, b, margin, manifold);
                        if (manifold.count > 0) {
                            ContactConstraint constraint = constraint(body, other, 0, 0, p, q);
                            constraint.update(manifold);
                            constraint.seen = true;
                        }
                    }
                }
            }
            if (canTouchTiles(self)) {
                tileBody = body;
                tileMargin = margin;
                host.tiles(
                        (int) Math.floor(minX - margin),
                        (int) Math.floor(minY - margin),
                        (int) Math.floor(maxX + margin),
                        (int) Math.floor(maxY + margin),
                        this::bodyTile);
            }
        }
        // Contacts of sleeping bodies stay as they were; the rest disappear when no longer found.
        for (Iterator<ContactConstraint> it = contacts.values().iterator(); it.hasNext(); ) {
            ContactConstraint c = it.next();
            if (c.seen) {
                continue;
            }
            boolean asleep = !c.a.awake && (c.b == null || !c.b.awake || !c.b.isDynamic());
            if (asleep) {
                c.seen = true;
                continue;
            }
            if (c.touching) {
                fireEnd(c);
            }
            it.remove();
        }
        active.clear();
        var events = host.events();
        boolean pre = events.hasListeners(PreCollideEvent.class);
        for (ContactConstraint c : contacts.values()) {
            c.touching = c.count > 0 && c.minSeparation() < Collide.SLOP;
            if (!c.a.awake && (c.b == null || !c.b.awake)) {
                continue;
            }
            if (c.b != null && c.b.isDynamic() && !c.b.awake) {
                c.b.wake();
            }
            if (!c.a.awake) {
                c.a.wake();
            }
            if (c.touching && pre) {
                Proxy other = c.other;
                PreCollideEvent event = events.call(new PreCollideEvent(
                        c.a.proxy.entity, other != null ? other.entity : null, new Vec2(-c.nx, -c.ny)));
                c.disabled = event.isCancelled();
            }
            if (!c.disabled) {
                active.add(c);
            }
        }
    }

    private BodySim tileBody;
    private float tileMargin;

    private void bodyTile(int tx, int ty, TileType type, int flags) {
        BodySim body = tileBody;
        List<Convex> pieces = tilePieces(type, flags);
        Proxy self = body.proxy;
        for (int q = 0; q < pieces.size(); q++) {
            Placed b = placedB.set(pieces.get(q), tx, ty);
            for (int p = 0; p < self.pieces.size(); p++) {
                Placed a = placedA.get(p);
                if (!b.overlapsBox(
                        a.minX - tileMargin, a.minY - tileMargin, a.maxX + tileMargin, a.maxY + tileMargin)) {
                    continue;
                }
                Collide.collide(a, b, tileMargin, manifold);
                if (manifold.count == 0 || internalTileEdge(tx, ty, -manifold.nx, -manifold.ny)) {
                    continue;
                }
                if (type.shape().isOneWay() && (manifold.ny < 0.7f || body.vy < -0.01f)) {
                    // One-way tiles hold only bodies falling onto them from above.
                    continue;
                }
                ContactConstraint constraint = constraint(body, null, tx, ty, p, q);
                constraint.update(manifold);
                constraint.seen = true;
                constraint.friction = type.friction();
            }
        }
    }

    private void ensurePlaced(int count) {
        while (placedA.size() < count) {
            placedA.add(new Placed());
        }
    }

    private boolean jointed(BodySim a, @Nullable BodySim b) {
        if (b == null) {
            return false;
        }
        for (int i = 0; i < joints.size(); i++) {
            Joints.JointImpl joint = joints.get(i);
            if (!joint.collideConnected && ((joint.a == a && joint.b == b) || (joint.a == b && joint.b == a))) {
                return true;
            }
        }
        return false;
    }

    private ContactConstraint constraint(BodySim a, @Nullable Proxy other, int tx, int ty, int pa, int pb) {
        probe.a = a.proxy.serial;
        probe.other = other != null ? other.serial : tileKey(tx, ty);
        probe.pieceA = pa;
        probe.pieceB = pb;
        ContactConstraint c = contacts.get(probe);
        if (c == null) {
            BodySim otherBody = other != null ? other.body : null;
            c = new ContactConstraint(a, otherBody, other, tx, ty, pa, pb);
            contacts.put(probe.copy(), c);
        }
        return c;
    }

    // ------------------------------------------------------------------ solver

    private void solve(float dt) {
        int n = subSteps;
        float h = dt / n;
        float invH = 1f / h;
        contactSoft.set(Math.min(CONTACT_HERTZ, 0.25f * n / dt), CONTACT_DAMPING, h);
        for (int i = 0; i < bodies.size(); i++) {
            BodySim b = bodies.get(i);
            b.cx0 = b.cx;
            b.cy0 = b.cy;
            b.angle0 = b.angle;
        }
        prepareContacts();
        for (int step = 0; step < n; step++) {
            for (int i = 0; i < bodies.size(); i++) {
                BodySim b = bodies.get(i);
                if (!b.isDynamic() || !b.awake) {
                    continue;
                }
                Body config = b.body;
                float gs = config.gravityScale();
                b.vx += h * (gravity.x() * gs + b.invMass * b.fx);
                b.vy += h * (gravity.y() * gs + b.invMass * b.fy);
                b.w += h * b.invI * b.torque;
                b.vx /= 1f + h * config.linearDamping();
                b.vy /= 1f + h * config.linearDamping();
                b.w /= 1f + h * config.angularDamping();
            }
            for (int j = 0; j < joints.size(); j++) {
                if (jointActive(joints.get(j))) {
                    joints.get(j).warmStart();
                }
            }
            warmStartContacts();
            for (int j = 0; j < joints.size(); j++) {
                if (jointActive(joints.get(j))) {
                    joints.get(j).solve(h, true);
                }
            }
            solveContacts(invH, true, (step & 1) == 1);
            for (int i = 0; i < bodies.size(); i++) {
                BodySim b = bodies.get(i);
                if (b.type == BodyType.STATIC || !b.awake) {
                    continue;
                }
                b.cx += h * b.vx;
                b.cy += h * b.vy;
                b.angle += h * b.w;
            }
            for (int j = 0; j < joints.size(); j++) {
                if (jointActive(joints.get(j))) {
                    joints.get(j).solve(h, false);
                }
            }
            solveContacts(invH, false, (step & 1) == 0);
        }
        applyRestitution();
        continuous(dt);
        for (int i = 0; i < bodies.size(); i++) {
            BodySim b = bodies.get(i);
            b.fx = 0f;
            b.fy = 0f;
            b.torque = 0f;
            if (b.type != BodyType.STATIC && b.awake) {
                b.writeToEntity();
                b.proxy.updateBounds(0.1f);
                broadphase.moved(b.proxy);
            }
        }
    }

    private static boolean jointActive(Joints.JointImpl joint) {
        return joint.b.awake || (joint.a != null && joint.a.awake);
    }

    private void prepareContacts() {
        for (int k = 0; k < active.size(); k++) {
            ContactConstraint c = active.get(k);
            BodySim a = c.a;
            BodySim b = c.b;
            Proxy other = c.other;
            float mA = a.invMass;
            float iA = a.invI;
            float mB = b != null ? b.invMass : 0f;
            float iB = b != null ? b.invI : 0f;
            c.otherVx = 0f;
            c.otherVy = 0f;
            float frictionB;
            float restitutionB = 0f;
            if (b != null) {
                frictionB = b.body.friction();
                restitutionB = b.body.restitution();
            } else if (other != null) {
                frictionB = 0.6f;
                Mover mover = other.mover;
                if (mover != null) {
                    c.otherVx = mover.velocity().x();
                    c.otherVy = mover.velocity().y();
                }
            } else {
                frictionB = c.friction > 0f ? c.friction : 0.6f;
            }
            c.friction = (float) Math.sqrt(a.body.friction() * frictionB);
            c.restitution = Math.max(a.body.restitution(), restitutionB);
            float nx = c.nx;
            float ny = c.ny;
            float tx = -ny;
            float ty = nx;
            for (int p = 0; p < c.count; p++) {
                float rax = c.px[p] - a.cx;
                float ray = c.py[p] - a.cy;
                float rbx = b != null ? c.px[p] - b.cx : 0f;
                float rby = b != null ? c.py[p] - b.cy : 0f;
                c.rax[p] = rax;
                c.ray[p] = ray;
                c.rbx[p] = rbx;
                c.rby[p] = rby;
                c.adjustedSeparation[p] = c.separation[p] - ((rbx - rax) * nx + (rby - ray) * ny);
                float rnA = rax * ny - ray * nx;
                float rnB = rbx * ny - rby * nx;
                float kN = mA + mB + iA * rnA * rnA + iB * rnB * rnB;
                c.normalMass[p] = kN > 0f ? 1f / kN : 0f;
                float rtA = rax * ty - ray * tx;
                float rtB = rbx * ty - rby * tx;
                float kT = mA + mB + iA * rtA * rtA + iB * rtB * rtB;
                c.tangentMass[p] = kT > 0f ? 1f / kT : 0f;
                c.relativeVelocity[p] = nx * relVx(c, p) + ny * relVy(c, p);
                c.maxNormalImpulse[p] = 0f;
            }
        }
    }

    private static float relVx(ContactConstraint c, int p) {
        BodySim a = c.a;
        BodySim b = c.b;
        float vbx = b != null ? b.vx - b.w * c.rby[p] : c.otherVx;
        return vbx - (a.vx - a.w * c.ray[p]);
    }

    private static float relVy(ContactConstraint c, int p) {
        BodySim a = c.a;
        BodySim b = c.b;
        float vby = b != null ? b.vy + b.w * c.rbx[p] : c.otherVy;
        return vby - (a.vy + a.w * c.rax[p]);
    }

    private static void applyImpulse(ContactConstraint c, int p, float px, float py) {
        BodySim a = c.a;
        a.vx -= a.invMass * px;
        a.vy -= a.invMass * py;
        a.w -= a.invI * (c.rax[p] * py - c.ray[p] * px);
        BodySim b = c.b;
        if (b != null) {
            b.vx += b.invMass * px;
            b.vy += b.invMass * py;
            b.w += b.invI * (c.rbx[p] * py - c.rby[p] * px);
        }
    }

    private void warmStartContacts() {
        for (int k = 0; k < active.size(); k++) {
            ContactConstraint c = active.get(k);
            float tx = -c.ny;
            float ty = c.nx;
            for (int p = 0; p < c.count; p++) {
                float pxN = c.normalImpulse[p] * c.nx + c.tangentImpulse[p] * tx;
                float pyN = c.normalImpulse[p] * c.ny + c.tangentImpulse[p] * ty;
                applyImpulse(c, p, pxN, pyN);
            }
        }
    }

    /**
     * One pass over the contacts. The order flips between passes: always solving the same point first makes a hard,
     * level landing tip the body over, because the first corner takes the whole impact.
     */
    private void solveContacts(float invH, boolean useBias, boolean backwards) {
        int count = active.size();
        for (int visit = 0; visit < count; visit++) {
            ContactConstraint c = active.get(backwards ? count - 1 - visit : visit);
            BodySim a = c.a;
            BodySim b = c.b;
            float nx = c.nx;
            float ny = c.ny;
            float dAngleA = a.angle - a.angle0;
            float cosA = (float) Math.cos(dAngleA);
            float sinA = (float) Math.sin(dAngleA);
            float dAngleB = b != null ? b.angle - b.angle0 : 0f;
            float cosB = (float) Math.cos(dAngleB);
            float sinB = (float) Math.sin(dAngleB);
            boolean hard = true;
            for (int p = 0; p < c.count; p++) {
                float prax = cosA * c.rax[p] - sinA * c.ray[p];
                float pray = sinA * c.rax[p] + cosA * c.ray[p];
                float dax = a.cx - a.cx0 + prax;
                float day = a.cy - a.cy0 + pray;
                float dbx = 0f;
                float dby = 0f;
                if (b != null) {
                    dbx = b.cx - b.cx0 + cosB * c.rbx[p] - sinB * c.rby[p];
                    dby = b.cy - b.cy0 + sinB * c.rbx[p] + cosB * c.rby[p];
                }
                float s = (dbx - dax) * nx + (dby - day) * ny + c.adjustedSeparation[p];
                currentSeparation[p] = s;
                hard &= s > 0f || !useBias;
            }
            if (c.count == 2 && hard && blockSolve(c, invH)) {
                friction(c, backwards);
                continue;
            }
            for (int visitPoint = 0; visitPoint < c.count; visitPoint++) {
                int p = backwards ? c.count - 1 - visitPoint : visitPoint;
                // Current separation from how far the bodies moved and turned since the tick started.
                float prax = cosA * c.rax[p] - sinA * c.ray[p];
                float pray = sinA * c.rax[p] + cosA * c.ray[p];
                float dax = a.cx - a.cx0 + prax;
                float day = a.cy - a.cy0 + pray;
                float dbx = 0f;
                float dby = 0f;
                if (b != null) {
                    dbx = b.cx - b.cx0 + cosB * c.rbx[p] - sinB * c.rby[p];
                    dby = b.cy - b.cy0 + sinB * c.rbx[p] + cosB * c.rby[p];
                }
                float s = (dbx - dax) * nx + (dby - day) * ny + c.adjustedSeparation[p];
                float bias = 0f;
                float massScale = 1f;
                float impulseScale = 0f;
                if (s > 0f) {
                    bias = s * invH;
                } else if (useBias) {
                    bias = Math.max(contactSoft.biasRate * s, -MAX_PUSH);
                    massScale = contactSoft.massScale;
                    impulseScale = contactSoft.impulseScale;
                }
                float vn = nx * relVx(c, p) + ny * relVy(c, p);
                float impulse = -c.normalMass[p] * massScale * (vn + bias) - impulseScale * c.normalImpulse[p];
                float next = Math.max(c.normalImpulse[p] + impulse, 0f);
                impulse = next - c.normalImpulse[p];
                c.normalImpulse[p] = next;
                c.maxNormalImpulse[p] = Math.max(c.maxNormalImpulse[p], impulse);
                applyImpulse(c, p, impulse * nx, impulse * ny);
            }
            friction(c, backwards);
        }
    }

    private final float[] currentSeparation = new float[2];

    private static void friction(ContactConstraint c, boolean backwards) {
        float tx = -c.ny;
        float ty = c.nx;
        for (int visitPoint = 0; visitPoint < c.count; visitPoint++) {
            int p = backwards ? c.count - 1 - visitPoint : visitPoint;
            float vt = tx * relVx(c, p) + ty * relVy(c, p);
            float impulse = -c.tangentMass[p] * vt;
            float limit = c.friction * c.normalImpulse[p];
            float next = Math.max(-limit, Math.min(limit, c.tangentImpulse[p] + impulse));
            impulse = next - c.tangentImpulse[p];
            c.tangentImpulse[p] = next;
            applyImpulse(c, p, impulse * tx, impulse * ty);
        }
    }

    /**
     * Solves both normal impulses of a two-point contact together (a 2x2 linear complementarity problem), so a flat
     * landing stops both corners at once instead of tipping the body. Returns {@code false} when the points are too
     * close for a well-conditioned solve; the caller then solves them one by one.
     */
    private boolean blockSolve(ContactConstraint c, float invH) {
        BodySim a = c.a;
        BodySim b = c.b;
        float mA = a.invMass;
        float iA = a.invI;
        float mB = b != null ? b.invMass : 0f;
        float iB = b != null ? b.invI : 0f;
        float nx = c.nx;
        float ny = c.ny;
        float rn1A = c.rax[0] * ny - c.ray[0] * nx;
        float rn1B = c.rbx[0] * ny - c.rby[0] * nx;
        float rn2A = c.rax[1] * ny - c.ray[1] * nx;
        float rn2B = c.rbx[1] * ny - c.rby[1] * nx;
        float k11 = mA + mB + iA * rn1A * rn1A + iB * rn1B * rn1B;
        float k22 = mA + mB + iA * rn2A * rn2A + iB * rn2B * rn2B;
        float k12 = mA + mB + iA * rn1A * rn2A + iB * rn1B * rn2B;
        float det = k11 * k22 - k12 * k12;
        if (k11 * k11 >= 1000f * det || det <= 0f) {
            return false;
        }
        float a1 = c.normalImpulse[0];
        float a2 = c.normalImpulse[1];
        float s1 = currentSeparation[0];
        float s2 = currentSeparation[1];
        float vn1 = nx * relVx(c, 0) + ny * relVy(c, 0) + (s1 > 0f ? s1 * invH : 0f);
        float vn2 = nx * relVx(c, 1) + ny * relVy(c, 1) + (s2 > 0f ? s2 * invH : 0f);
        // w = K x + b with b = vn - K a; find x >= 0, w >= 0, x.w = 0.
        float b1 = vn1 - (k11 * a1 + k12 * a2);
        float b2 = vn2 - (k12 * a1 + k22 * a2);
        float x1;
        float x2;
        float inv = 1f / det;
        x1 = -inv * (k22 * b1 - k12 * b2);
        x2 = -inv * (k11 * b2 - k12 * b1);
        if (!(x1 >= 0f && x2 >= 0f)) {
            x1 = -b1 / k11;
            x2 = 0f;
            if (!(x1 >= 0f && k12 * x1 + b2 >= 0f)) {
                x1 = 0f;
                x2 = -b2 / k22;
                if (!(x2 >= 0f && k12 * x2 + b1 >= 0f)) {
                    x1 = 0f;
                    x2 = 0f;
                    if (!(b1 >= 0f && b2 >= 0f)) {
                        return false;
                    }
                }
            }
        }
        float d1 = x1 - a1;
        float d2 = x2 - a2;
        c.normalImpulse[0] = x1;
        c.normalImpulse[1] = x2;
        c.maxNormalImpulse[0] = Math.max(c.maxNormalImpulse[0], d1);
        c.maxNormalImpulse[1] = Math.max(c.maxNormalImpulse[1], d2);
        applyImpulse(c, 0, d1 * nx, d1 * ny);
        applyImpulse(c, 1, d2 * nx, d2 * ny);
        return true;
    }

    private void applyRestitution() {
        for (int k = 0; k < active.size(); k++) {
            ContactConstraint c = active.get(k);
            if (c.restitution == 0f) {
                continue;
            }
            for (int p = 0; p < c.count; p++) {
                if (c.relativeVelocity[p] > -RESTITUTION_THRESHOLD || c.maxNormalImpulse[p] == 0f) {
                    continue;
                }
                float vn = c.nx * relVx(c, p) + c.ny * relVy(c, p);
                float impulse = -c.normalMass[p] * (vn + c.restitution * c.relativeVelocity[p]);
                float next = Math.max(c.normalImpulse[p] + impulse, 0f);
                impulse = next - c.normalImpulse[p];
                c.normalImpulse[p] = next;
                applyImpulse(c, p, impulse * c.nx, impulse * c.ny);
            }
        }
    }

    /** Stops fast bodies at the first static obstacle along their motion in this tick. */
    private void continuous(float dt) {
        for (int i = 0; i < bodies.size(); i++) {
            BodySim b = bodies.get(i);
            if (!b.isDynamic() || !b.awake) {
                continue;
            }
            float mx = b.cx - b.cx0;
            float my = b.cy - b.cy0;
            float moved = (float) Math.sqrt(mx * mx + my * my);
            float size = Math.max(0.05f, b.proxy.extent * 0.5f);
            if (!b.body.isBullet() && moved < size) {
                continue;
            }
            float cos = (float) Math.cos(b.angle);
            float sin = (float) Math.sin(b.angle);
            float originX0 = b.cx0 - (cos * b.localCx - sin * b.localCy);
            float originY0 = b.cy0 - (sin * b.localCx + cos * b.localCy);
            sweepExclude = b.proxy;
            sweepStaticOnly = !b.body.isBullet();
            RayHit hit = sweep(b.proxy.pieces, cos, sin, originX0, originY0, mx, my, b.proxy.maskBits, b.proxy.layer);
            sweepExclude = null;
            if (hit != null && hit.fraction() < 1f) {
                float t = Math.max(0f, hit.fraction());
                b.cx = b.cx0 + mx * t;
                b.cy = b.cy0 + my * t;
                float into = b.vx * hit.normal().x() + b.vy * hit.normal().y();
                if (into < 0f) {
                    b.vx -= hit.normal().x() * into;
                    b.vy -= hit.normal().y() * into;
                }
            }
        }
    }

    private @Nullable Proxy sweepExclude;
    private boolean sweepStaticOnly;

    private void reportContacts() {
        var events = host.events();
        boolean begin = events.hasListeners(EntityCollideEvent.class);
        boolean land = events.hasListeners(EntityLandEvent.class);
        for (int i = 0; i < bodies.size(); i++) {
            BodySim b = bodies.get(i);
            b.island = b.onFloor ? 1 : 0;
            b.onFloor = false;
        }
        for (ContactConstraint c : contacts.values()) {
            if (!c.touching || c.disabled) {
                if (c.wasTouching && !c.touching) {
                    fireEnd(c);
                }
                continue;
            }
            if (c.ny > 0.7f) {
                c.a.onFloor = true;
            }
            if (c.b != null && c.ny < -0.7f) {
                c.b.onFloor = true;
            }
            if (!c.wasTouching && begin) {
                float impulse = 0f;
                for (int p = 0; p < c.count; p++) {
                    impulse += c.maxNormalImpulse[p];
                }
                impulse *= subSteps;
                Vec2 point = new Vec2(c.px[0], c.py[0]);
                Proxy other = c.other;
                Entity otherEntity = other != null ? other.entity : null;
                GridPos tile = other == null ? new GridPos(c.tileX, c.tileY) : null;
                events.call(new EntityCollideEvent(
                        c.a.proxy.entity, otherEntity, tile, point, new Vec2(-c.nx, -c.ny), impulse));
                if (otherEntity != null && !otherEntity.isRemoved()) {
                    events.call(new EntityCollideEvent(
                            otherEntity, c.a.proxy.entity, null, point, new Vec2(c.nx, c.ny), impulse));
                }
            }
        }
        for (int i = 0; i < bodies.size(); i++) {
            BodySim b = bodies.get(i);
            boolean wasOnFloor = b.island == 1;
            if (land && b.isDynamic() && b.onFloor && !wasOnFloor) {
                events.call(new EntityLandEvent(b.proxy.entity, Math.max(0f, b.vy), Vec2.UP));
            }
        }
    }

    private void fireEnd(ContactConstraint c) {
        var events = host.events();
        if (!events.hasListeners(EntityCollideEndEvent.class)) {
            return;
        }
        Proxy other = c.other;
        Entity otherEntity = other != null ? other.entity : null;
        events.call(new EntityCollideEndEvent(
                c.a.proxy.entity, otherEntity, other == null ? new GridPos(c.tileX, c.tileY) : null));
        if (otherEntity != null) {
            events.call(new EntityCollideEndEvent(otherEntity, c.a.proxy.entity, null));
        }
    }

    // ------------------------------------------------------------------ sleep

    private int[] parent = new int[16];
    private float[] islandTime = new float[16];

    private void sleep(float dt) {
        int n = bodies.size();
        if (parent.length < n) {
            parent = new int[n * 2];
        }
        for (int i = 0; i < n; i++) {
            BodySim b = bodies.get(i);
            b.island = i;
            parent[i] = i;
            if (!b.isDynamic() || !b.awake) {
                continue;
            }
            boolean still = b.vx * b.vx + b.vy * b.vy <= LINEAR_SLEEP * LINEAR_SLEEP && Math.abs(b.w) <= ANGULAR_SLEEP;
            b.sleepTime = still && b.body.isSleepingAllowed() ? b.sleepTime + dt : 0f;
        }
        for (ContactConstraint c : contacts.values()) {
            BodySim b = c.b;
            if (c.touching && b != null && b.isDynamic() && c.a.awake && b.awake) {
                union(c.a.island, b.island);
            }
        }
        for (Joints.JointImpl joint : joints) {
            BodySim a = joint.a;
            if (a != null && a.isDynamic() && joint.b.isDynamic()) {
                if (a.awake != joint.b.awake) {
                    a.wake();
                    joint.b.wake();
                }
                union(a.island, joint.b.island);
            }
        }
        if (islandTime.length < n) {
            islandTime = new float[parent.length];
        }
        java.util.Arrays.fill(islandTime, 0, n, Float.MAX_VALUE);
        for (int i = 0; i < n; i++) {
            BodySim b = bodies.get(i);
            if (b.isDynamic() && b.awake) {
                int root = find(i);
                islandTime[root] = Math.min(islandTime[root], b.sleepTime);
            }
        }
        for (int i = 0; i < n; i++) {
            BodySim b = bodies.get(i);
            if (b.isDynamic() && b.awake && islandTime[find(i)] >= TIME_TO_SLEEP) {
                b.awake = false;
                b.vx = 0f;
                b.vy = 0f;
                b.w = 0f;
            }
        }
    }

    private int find(int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private void union(int a, int b) {
        int ra = find(a);
        int rb = find(b);
        if (ra != rb) {
            parent[ra] = rb;
        }
    }

    // ------------------------------------------------------------------ triggers

    private final class TriggerState {
        final Trigger trigger;
        final List<Entity> inside = new ArrayList<>();
        final List<Entity> now = new ArrayList<>();
        List<Convex> pieces = List.of();

        @Nullable Object key;

        float keyWidth = Float.NaN;
        float keyHeight = Float.NaN;

        TriggerState(Trigger trigger) {
            this.trigger = trigger;
        }

        void refresh() {
            Entity entity = trigger.entity();
            Shape shape = trigger.shape();
            float ox = trigger.offset().x();
            float oy = trigger.offset().y();
            if (shape == null) {
                Collider collider = entity.find(Collider.class).orElse(null);
                if (collider != null && collider.shape() != null) {
                    shape = collider.shape();
                    ox += collider.offset().x();
                    oy += collider.offset().y();
                }
            }
            Vec2 size = entity.size();
            Object k = shape != null ? shape : size;
            if (k != key || (shape == null && (size.x() != keyWidth || size.y() != keyHeight))) {
                key = k;
                keyWidth = size.x();
                keyHeight = size.y();
                pieces = shape != null ? Shapes.pieces(shape, ox, oy) : Shapes.box(size.x(), size.y(), ox, oy);
            }
        }
    }

    private final List<TriggerState> triggerSnapshot = new ArrayList<>();

    private void updateTriggers() {
        var events = host.events();
        triggerSnapshot.clear();
        triggerSnapshot.addAll(triggers);
        for (int t = 0; t < triggerSnapshot.size(); t++) {
            TriggerState state = triggerSnapshot.get(t);
            if (triggerByComponent.get(state.trigger) != state) {
                continue;
            }
            Trigger trigger = state.trigger;
            if (!trigger.isAttached() || trigger.entity().isRemoved()) {
                continue;
            }
            if (!trigger.isEnabled()) {
                state.inside.clear();
                continue;
            }
            Entity self = trigger.entity();
            state.refresh();
            int layerBit = trigger.layer().bit() >= 0 ? 1 << trigger.layer().bit() : 0;
            int maskBits = trigger.mask().bits();
            float angle = (float) Math.toRadians(self.rotation());
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            ensurePlaced(state.pieces.size());
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;
            for (int p = 0; p < state.pieces.size(); p++) {
                Placed placed = placedA.get(p).set(state.pieces.get(p), self.x(), self.y(), cos, sin);
                minX = Math.min(minX, placed.minX);
                minY = Math.min(minY, placed.minY);
                maxX = Math.max(maxX, placed.maxX);
                maxY = Math.max(maxY, placed.maxY);
            }
            candidates.clear();
            broadphase.query(minX, minY, maxX, maxY, collect);
            state.now.clear();
            for (int c = 0; c < candidates.size(); c++) {
                Proxy other = candidates.get(c);
                if (other.entity == self
                        || other.entity.isRemoved()
                        || (maskBits & other.layerBit) == 0
                        || (other.maskBits & layerBit) == 0
                        || !matrixAllows(trigger.layer(), other.layer)) {
                    continue;
                }
                boolean overlap = false;
                for (int p = 0; p < state.pieces.size() && !overlap; p++) {
                    for (int q = 0; q < other.pieces.size() && !overlap; q++) {
                        overlap = Collide.overlaps(placedA.get(p), other.place(q, placedB), manifold);
                    }
                }
                if (overlap) {
                    state.now.add(other.entity);
                }
            }
            for (int i = state.inside.size() - 1; i >= 0; i--) {
                Entity was = state.inside.get(i);
                if (!state.now.contains(was)) {
                    state.inside.remove(i);
                    if (events.hasListeners(TriggerExitEvent.class)) {
                        events.call(new TriggerExitEvent(trigger, was));
                    }
                }
            }
            for (int i = 0; i < state.now.size(); i++) {
                Entity entered = state.now.get(i);
                if (!state.inside.contains(entered)) {
                    state.inside.add(entered);
                    if (events.hasListeners(TriggerEnterEvent.class)) {
                        events.call(new TriggerEnterEvent(trigger, entered));
                    }
                    if (!trigger.isEnabled() || !trigger.isAttached()) {
                        break;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ Physics

    @Override
    public Vec2 gravity() {
        return gravity;
    }

    @Override
    public void setGravity(Vec2 value) {
        gravity = value;
        for (BodySim b : bodies) {
            b.wake();
        }
    }

    @Override
    public int subSteps() {
        return subSteps;
    }

    @Override
    public void setSubSteps(int value) {
        if (value < 1 || value > 16) {
            throw new IllegalArgumentException("Substeps must be 1..16: " + value);
        }
        subSteps = value;
    }

    @Override
    public void setCollides(CollisionLayer a, CollisionLayer b, boolean collide) {
        int ba = a.bit();
        int bb = b.bit();
        if (ba < 0 || bb < 0) {
            throw new IllegalArgumentException("Register the collision layers before changing the matrix");
        }
        if (collide) {
            matrix[ba] |= 1 << bb;
            matrix[bb] |= 1 << ba;
        } else {
            matrix[ba] &= ~(1 << bb);
            matrix[bb] &= ~(1 << ba);
        }
    }

    @Override
    public boolean collides(CollisionLayer a, CollisionLayer b) {
        return matrixAllows(a, b);
    }

    @Override
    public @Nullable RayHit raycast(Vec2 from, Vec2 to) {
        return raycast(from, to, CollisionMask.ALL);
    }

    @Override
    public @Nullable RayHit raycast(Vec2 from, Vec2 to, CollisionMask mask) {
        List<RayHit> hits = rays(from, to, mask, false);
        return hits.isEmpty() ? null : hits.get(0);
    }

    @Override
    public List<RayHit> raycastAll(Vec2 from, Vec2 to, CollisionMask mask) {
        return rays(from, to, mask, true);
    }

    private List<RayHit> rays(Vec2 from, Vec2 to, CollisionMask mask, boolean all) {
        List<RayHit> hits = new ArrayList<>();
        float ox = from.x();
        float oy = from.y();
        float dx = to.x() - ox;
        float dy = to.y() - oy;
        int maskBits = mask.bits();
        candidates.clear();
        broadphase.query(
                Math.min(ox, to.x()), Math.min(oy, to.y()), Math.max(ox, to.x()), Math.max(oy, to.y()), collect);
        float best = 1f;
        for (int c = 0; c < candidates.size(); c++) {
            Proxy proxy = candidates.get(c);
            if ((maskBits & proxy.layerBit) == 0 || proxy.pieces.isEmpty()) {
                continue;
            }
            float nearest = all ? 1f : best;
            boolean hit = false;
            float nx = 0f;
            float ny = 0f;
            for (int p = 0; p < proxy.pieces.size(); p++) {
                if (Collide.raycast(proxy.place(p, scratch), ox, oy, dx, dy, nearest)) {
                    hit = true;
                    nearest = Collide.rayFraction;
                    nx = Collide.rayNormalX;
                    ny = Collide.rayNormalY;
                }
            }
            if (hit) {
                hits.add(new RayHit(
                        proxy.entity, null, new Vec2(ox + dx * nearest, oy + dy * nearest), new Vec2(nx, ny), nearest));
                best = Math.min(best, nearest);
            }
        }
        if ((maskBits & tilesBit()) != 0) {
            tileRay(ox, oy, dx, dy, all ? 1f : best, all, hits);
        }
        hits.sort((a, b) -> Float.compare(a.fraction(), b.fraction()));
        if (!all && hits.size() > 1) {
            return List.of(hits.get(0));
        }
        return hits;
    }

    /** Walks the cells along a ray and casts against the tiles in each. */
    private void tileRay(float ox, float oy, float dx, float dy, float maxFraction, boolean all, List<RayHit> hits) {
        int cx = (int) Math.floor(ox);
        int cy = (int) Math.floor(oy);
        int endX = (int) Math.floor(ox + dx * maxFraction);
        int endY = (int) Math.floor(oy + dy * maxFraction);
        int stepX = dx > 0f ? 1 : dx < 0f ? -1 : 0;
        int stepY = dy > 0f ? 1 : dy < 0f ? -1 : 0;
        float tDeltaX = stepX != 0 ? Math.abs(1f / dx) : Float.MAX_VALUE;
        float tDeltaY = stepY != 0 ? Math.abs(1f / dy) : Float.MAX_VALUE;
        float tMaxX = stepX > 0 ? (cx + 1 - ox) / dx : stepX < 0 ? (ox - cx) / -dx : Float.MAX_VALUE;
        float tMaxY = stepY > 0 ? (cy + 1 - oy) / dy : stepY < 0 ? (oy - cy) / -dy : Float.MAX_VALUE;
        int guard = Math.abs(endX - cx) + Math.abs(endY - cy) + 2;
        for (int i = 0; i <= guard; i++) {
            rayCellHit = false;
            rayCellBest = maxFraction;
            rayOx = ox;
            rayOy = oy;
            rayDx = dx;
            rayDy = dy;
            host.tiles(cx, cy, cx, cy, this::rayTile);
            if (rayCellHit) {
                hits.add(new RayHit(
                        null,
                        new GridPos(cx, cy),
                        new Vec2(ox + dx * rayCellBest, oy + dy * rayCellBest),
                        new Vec2(rayCellNx, rayCellNy),
                        rayCellBest));
                if (!all) {
                    return;
                }
            }
            if (cx == endX && cy == endY) {
                return;
            }
            if (tMaxX < tMaxY) {
                if (tMaxX > maxFraction) {
                    return;
                }
                tMaxX += tDeltaX;
                cx += stepX;
            } else {
                if (tMaxY > maxFraction) {
                    return;
                }
                tMaxY += tDeltaY;
                cy += stepY;
            }
        }
    }

    private boolean rayCellHit;
    private float rayCellBest;
    private float rayCellNx;
    private float rayCellNy;
    private float rayOx;
    private float rayOy;
    private float rayDx;
    private float rayDy;

    private void rayTile(int tx, int ty, TileType type, int flags) {
        List<Convex> pieces = tilePieces(type, flags);
        for (int i = 0; i < pieces.size(); i++) {
            if (Collide.raycast(scratch.set(pieces.get(i), tx, ty), rayOx, rayOy, rayDx, rayDy, rayCellBest)) {
                rayCellHit = true;
                rayCellBest = Collide.rayFraction;
                rayCellNx = Collide.rayNormalX;
                rayCellNy = Collide.rayNormalY;
            }
        }
    }

    @Override
    public @Nullable RayHit shapeCast(Shape shape, Vec2 from, Vec2 to, CollisionMask mask) {
        List<Convex> pieces = Shapes.pieces(shape, 0f, 0f);
        sweepExclude = null;
        sweepStaticOnly = false;
        return sweep(pieces, 1f, 0f, from.x(), from.y(), to.x() - from.x(), to.y() - from.y(), mask.bits(), null);
    }

    private final List<Placed> sweepPieces = new ArrayList<>();
    private final List<Proxy> sweepFound = new ArrayList<>();

    /**
     * Conservative advancement of pieces moving from an origin by {@code (mx, my)}: repeatedly moves by the smallest
     * distance to any obstacle until touching.
     */
    private @Nullable RayHit sweep(
            List<Convex> pieces,
            float cos,
            float sin,
            float ox,
            float oy,
            float mx,
            float my,
            int maskBits,
            @Nullable CollisionLayer layer) {
        float length = (float) Math.sqrt(mx * mx + my * my);
        while (sweepPieces.size() < pieces.size()) {
            sweepPieces.add(new Placed());
        }
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int p = 0; p < pieces.size(); p++) {
            Placed placed = sweepPieces.get(p).set(pieces.get(p), ox, oy, cos, sin);
            minX = Math.min(minX, Math.min(placed.minX, placed.minX + mx));
            minY = Math.min(minY, Math.min(placed.minY, placed.minY + my));
            maxX = Math.max(maxX, Math.max(placed.maxX, placed.maxX + mx));
            maxY = Math.max(maxY, Math.max(placed.maxY, placed.maxY + my));
        }
        List<Proxy> found = sweepFound;
        found.clear();
        candidates.clear();
        broadphase.query(minX, minY, maxX, maxY, collect);
        for (Proxy proxy : candidates) {
            if (proxy == sweepExclude || proxy.pieces.isEmpty() || (maskBits & proxy.layerBit) == 0) {
                continue;
            }
            if (layer != null
                    && ((proxy.maskBits & (layer.bit() >= 0 ? 1 << layer.bit() : 0)) == 0
                            || !matrixAllows(layer, proxy.layer))) {
                continue;
            }
            BodySim body = proxy.body;
            if (sweepStaticOnly && body != null && body.isDynamic()) {
                continue;
            }
            found.add(proxy);
        }
        boolean tiles = (maskBits & tilesBit()) != 0;
        float t = 0f;
        float target = Collide.SLOP;
        for (int iteration = 0; iteration < 30; iteration++) {
            sweepBest = Float.MAX_VALUE;
            sweepHit = null;
            sweepHitTile = false;
            for (int p = 0; p < pieces.size(); p++) {
                Placed moving = sweepPieces.get(p).set(pieces.get(p), ox + mx * t, oy + my * t, cos, sin);
                for (Proxy proxy : found) {
                    for (int q = 0; q < proxy.pieces.size(); q++) {
                        measure(moving, proxy.place(q, placedB), proxy, 0, 0);
                    }
                }
                if (tiles) {
                    // Every tile in the swept area counts, not just those near the current position.
                    sweepMoving = moving;
                    host.tiles(
                            (int) Math.floor(minX),
                            (int) Math.floor(minY),
                            (int) Math.floor(maxX),
                            (int) Math.floor(maxY),
                            this::sweepTile);
                }
            }
            if (sweepBest == Float.MAX_VALUE) {
                return null;
            }
            if (sweepBest <= target) {
                if (t == 0f && sweepBest < 0f && sweepNx * mx + sweepNy * my >= 0f) {
                    // Starting inside something and moving away from it does not count.
                    return null;
                }
                Proxy hitProxy = sweepHit;
                return new RayHit(
                        hitProxy != null ? hitProxy.entity : null,
                        sweepHitTile ? new GridPos(sweepTileX, sweepTileY) : null,
                        new Vec2(sweepPx, sweepPy),
                        new Vec2(sweepNx, sweepNy),
                        t);
            }
            if (length <= 0f) {
                return null;
            }
            t += (sweepBest - target * 0.5f) / length;
            if (t > 1f) {
                return null;
            }
        }
        return null;
    }

    private float sweepBest;
    private @Nullable Proxy sweepHit;
    private boolean sweepHitTile;
    private int sweepTileX;
    private int sweepTileY;
    private float sweepNx;
    private float sweepNy;
    private float sweepPx;
    private float sweepPy;
    private Placed sweepMoving;

    private void measure(Placed moving, Placed fixed, @Nullable Proxy proxy, int tx, int ty) {
        Collide.distance(fixed, moving, manifold);
        if (manifold.count == 0) {
            return;
        }
        float s = manifold.minSeparation();
        if (s < sweepBest) {
            sweepBest = s;
            sweepHit = proxy;
            sweepHitTile = proxy == null;
            sweepTileX = tx;
            sweepTileY = ty;
            sweepNx = manifold.nx;
            sweepNy = manifold.ny;
            sweepPx = manifold.px[0];
            sweepPy = manifold.py[0];
        }
    }

    private void sweepTile(int tx, int ty, TileType type, int flags) {
        List<Convex> pieces = tilePieces(type, flags);
        for (int i = 0; i < pieces.size(); i++) {
            measure(sweepMoving, scratch.set(pieces.get(i), tx, ty), null, tx, ty);
        }
    }

    @Override
    public List<Entity> overlapPoint(Vec2 point, CollisionMask mask) {
        List<Entity> result = new ArrayList<>();
        int maskBits = mask.bits();
        candidates.clear();
        broadphase.query(point.x(), point.y(), point.x(), point.y(), collect);
        for (Proxy proxy : candidates) {
            if ((maskBits & proxy.layerBit) == 0) {
                continue;
            }
            for (int p = 0; p < proxy.pieces.size(); p++) {
                if (Collide.contains(proxy.place(p, scratch), point.x(), point.y())) {
                    result.add(proxy.entity);
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public List<Entity> overlapCircle(Vec2 center, float radius, CollisionMask mask) {
        Convex circle = new Convex(new float[] {0f}, new float[] {0f}, 1, radius);
        return overlapPiece(circle, center.x(), center.y(), mask);
    }

    @Override
    public List<Entity> overlapRect(Rect area, CollisionMask mask) {
        Convex box = Shapes.box(area.width(), area.height(), 0f, 0f).get(0);
        return overlapPiece(box, area.x() + area.width() / 2f, area.y() + area.height() / 2f, mask);
    }

    private List<Entity> overlapPiece(Convex piece, float x, float y, CollisionMask mask) {
        List<Entity> result = new ArrayList<>();
        Placed probe = new Placed().set(piece, x, y);
        int maskBits = mask.bits();
        candidates.clear();
        broadphase.query(probe.minX, probe.minY, probe.maxX, probe.maxY, collect);
        for (Proxy proxy : candidates) {
            if ((maskBits & proxy.layerBit) == 0) {
                continue;
            }
            for (int p = 0; p < proxy.pieces.size(); p++) {
                if (Collide.overlaps(probe, proxy.place(p, scratch), manifold)) {
                    result.add(proxy.entity);
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public boolean isSolidTile(Vec2 point) {
        int tx = (int) Math.floor(point.x());
        int ty = (int) Math.floor(point.y());
        solidProbeX = point.x();
        solidProbeY = point.y();
        solidFound = false;
        host.tiles(tx, ty, tx, ty, this::solidTile);
        return solidFound;
    }

    private float solidProbeX;
    private float solidProbeY;
    private boolean solidFound;

    private void solidTile(int tx, int ty, TileType type, int flags) {
        for (Convex piece : tilePieces(type, flags)) {
            if (Collide.contains(scratch.set(piece, tx, ty), solidProbeX, solidProbeY)) {
                solidFound = true;
            }
        }
    }

    // ------------------------------------------------------------------ joints

    private BodySim bodyOf(Entity entity) {
        Proxy proxy = proxies.get(entity);
        BodySim body = proxy != null ? proxy.body : null;
        if (body == null) {
            throw new IllegalArgumentException(entity + " has no spawned Body");
        }
        if (body.massChanged) {
            proxy.refresh();
            body.updateMass();
        }
        return body;
    }

    private <J extends Joints.JointImpl> J add(J joint) {
        joints.add(joint);
        joint.wake();
        BodySim a = joint.a;
        if (a != null) {
            dropContactsBetween(a, joint.b);
        }
        return joint;
    }

    private void dropContactsBetween(BodySim a, BodySim b) {
        contacts.values().removeIf(c -> (c.a == a && c.b == b) || (c.a == b && c.b == a));
    }

    @Override
    public DistanceJoint distance(Entity a, Entity b, Vec2 anchorA, Vec2 anchorB) {
        return add(new Joints.DistanceImpl(this, bodyOf(a), bodyOf(b), anchorA, anchorB));
    }

    @Override
    public RopeJoint rope(Entity a, Entity b, Vec2 anchorA, Vec2 anchorB, float maxLength) {
        return add(new Joints.RopeImpl(this, bodyOf(a), bodyOf(b), anchorA, anchorB, maxLength));
    }

    @Override
    public RevoluteJoint revolute(Entity a, Entity b, Vec2 anchor) {
        return add(new Joints.RevoluteImpl(this, bodyOf(a), bodyOf(b), anchor));
    }

    @Override
    public PrismaticJoint prismatic(Entity a, Entity b, Vec2 anchor, Vec2 axis) {
        return add(new Joints.PrismaticImpl(this, bodyOf(a), bodyOf(b), anchor, axis));
    }

    @Override
    public WeldJoint weld(Entity a, Entity b, Vec2 anchor) {
        return add(new Joints.WeldImpl(this, bodyOf(a), bodyOf(b), anchor));
    }

    @Override
    public WheelJoint wheel(Entity a, Entity b, Vec2 anchor, Vec2 axis) {
        return add(new Joints.WheelImpl(this, bodyOf(a), bodyOf(b), anchor, axis));
    }

    @Override
    public MouseJoint mouse(Entity body, Vec2 target) {
        BodySim sim = bodyOf(body);
        if (!sim.isDynamic()) {
            throw new IllegalArgumentException("A mouse joint needs a dynamic body");
        }
        return add(new Joints.MouseImpl(this, sim, target));
    }

    @Override
    public MotorJoint motor(Entity a, Entity b) {
        return add(new Joints.MotorImpl(this, bodyOf(a), bodyOf(b)));
    }

    @Override
    public List<Joint> joints() {
        List<Joint> result = new ArrayList<>(joints.size());
        for (Joints.JointImpl joint : joints) {
            result.add((Joint) joint);
        }
        return Collections.unmodifiableList(result);
    }

    void removeJoint(Joints.JointImpl joint) {
        if (joints.remove(joint)) {
            joint.valid = false;
            joint.wake();
        }
    }

    // ------------------------------------------------------------------ debug

    /** Which debug drawings to make; see {@link #debugDraw}. */
    public static final int SHAPES = 1;
    /** Contact points and normals. */
    public static final int CONTACTS = 2;
    /** Joints. */
    public static final int JOINTS = 4;
    /** Trigger areas. */
    public static final int TRIGGERS = 8;

    private static final Color SHAPE = Color.rgba(0x4fc3f7ff);
    private static final Color SLEEPING = Color.rgba(0x9e9e9eff);
    private static final Color STATIC = Color.rgba(0x81c784ff);
    private static final Color MOVER = Color.rgba(0xffb74dff);
    private static final Color TRIGGER = Color.rgba(0xf06292ff);
    private static final Color CONTACT = Color.rgba(0xffee58ff);
    private static final Color JOINT = Color.rgba(0xce93d8ff);

    /**
     * Draws the physics over the world.
     *
     * @param draw where to draw, in world units
     * @param what bit set of {@link #SHAPES}, {@link #CONTACTS}, {@link #JOINTS}, {@link #TRIGGERS}
     * @param view the visible area, to skip tiles out of sight
     * @param line line width in world units
     */
    public void debugDraw(Draw draw, int what, Rect view, float line) {
        if ((what & SHAPES) != 0) {
            for (Proxy proxy : proxyList) {
                BodySim body = proxy.body;
                Color color = proxy.mover != null
                        ? MOVER
                        : body == null || body.type == BodyType.STATIC ? STATIC : body.awake ? SHAPE : SLEEPING;
                draw.color(color);
                for (int p = 0; p < proxy.pieces.size(); p++) {
                    outline(draw, proxy.place(p, scratch), line);
                }
            }
            draw.color(STATIC);
            debugDraw = draw;
            debugLine = line;
            host.tiles(
                    (int) Math.floor(view.x()),
                    (int) Math.floor(view.y()),
                    (int) Math.floor(view.x() + view.width()),
                    (int) Math.floor(view.y() + view.height()),
                    this::drawTile);
        }
        if ((what & TRIGGERS) != 0) {
            draw.color(TRIGGER);
            for (TriggerState state : triggers) {
                if (!state.trigger.isAttached()) {
                    continue;
                }
                Entity entity = state.trigger.entity();
                state.refresh();
                for (Convex piece : state.pieces) {
                    outline(draw, scratch.set(piece, entity.x(), entity.y()), line);
                }
            }
        }
        if ((what & CONTACTS) != 0) {
            draw.color(CONTACT);
            for (ContactConstraint c : contacts.values()) {
                if (!c.touching) {
                    continue;
                }
                for (int p = 0; p < c.count; p++) {
                    draw.circle(c.px[p], c.py[p], line * 2f);
                    draw.line(c.px[p], c.py[p], c.px[p] - c.nx * 0.3f, c.py[p] - c.ny * 0.3f, line);
                }
            }
        }
        if ((what & JOINTS) != 0) {
            draw.color(JOINT);
            float[] anchors = new float[4];
            for (Joints.JointImpl joint : joints) {
                joint.debugAnchors(anchors);
                BodySim a = joint.a;
                if (a != null) {
                    draw.line(a.cx, a.cy, anchors[0], anchors[1], line);
                }
                draw.line(anchors[0], anchors[1], anchors[2], anchors[3], line);
                draw.line(joint.b.cx, joint.b.cy, anchors[2], anchors[3], line);
                draw.circle(anchors[0], anchors[1], line * 2f);
            }
        }
        draw.color(Color.WHITE);
    }

    private Draw debugDraw;
    private float debugLine;

    private void drawTile(int tx, int ty, TileType type, int flags) {
        for (Convex piece : tilePieces(type, flags)) {
            outline(debugDraw, scratch.set(piece, tx, ty), debugLine);
        }
    }

    private static void outline(Draw draw, Placed p, float line) {
        if (p.count == 1) {
            draw.circleOutline(p.x[0], p.y[0], p.radius, line);
            return;
        }
        if (p.count == 2) {
            if (p.radius > 0f) {
                draw.circleOutline(p.x[0], p.y[0], p.radius, line);
                draw.circleOutline(p.x[1], p.y[1], p.radius, line);
                for (int side = 0; side < 2; side++) {
                    float ox = p.nx[side] * p.radius;
                    float oy = p.ny[side] * p.radius;
                    draw.line(p.x[0] + ox, p.y[0] + oy, p.x[1] + ox, p.y[1] + oy, line);
                }
            } else {
                draw.line(p.x[0], p.y[0], p.x[1], p.y[1], line);
            }
            return;
        }
        for (int i = 0; i < p.count; i++) {
            int j = (i + 1) % p.count;
            draw.line(p.x[i], p.y[i], p.x[j], p.y[j], line);
        }
    }

    /** Number of rigid bodies, for tests and statistics. */
    int bodyCount() {
        return bodies.size();
    }

    /** Number of awake dynamic bodies. */
    public int awakeBodies() {
        int count = 0;
        for (BodySim b : bodies) {
            if (b.isDynamic() && b.awake) {
                count++;
            }
        }
        return count;
    }

    /** Clears everything when the world unloads. */
    public void dispose() {
        broadphase.clear();
        proxies.clear();
        proxyList.clear();
        bodies.clear();
        triggers.clear();
        triggerByComponent.clear();
        joints.clear();
        contacts.clear();
    }

    /** Key of a contact: body, piece, other proxy or tile, other piece. Mutable for lookups; copied when stored. */
    private static final class ContactKey {
        int a;
        long other;
        int pieceA;
        int pieceB;

        ContactKey copy() {
            ContactKey key = new ContactKey();
            key.a = a;
            key.other = other;
            key.pieceA = pieceA;
            key.pieceB = pieceB;
            return key;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ContactKey k
                    && k.a == a
                    && k.other == other
                    && k.pieceA == pieceA
                    && k.pieceB == pieceB;
        }

        @Override
        public int hashCode() {
            int h = a * 31 + Long.hashCode(other);
            return (h * 31 + pieceA) * 31 + pieceB;
        }
    }
}
