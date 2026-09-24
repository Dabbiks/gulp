package dev.gulp.core.physics;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.math.GridPos;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.physics.Contact;
import dev.gulp.api.physics.EntityCollideEndEvent;
import dev.gulp.api.physics.EntityCollideEvent;
import dev.gulp.api.physics.EntityLandEvent;
import dev.gulp.api.physics.Mover;
import dev.gulp.api.spi.PhysicsAccess;
import dev.gulp.api.world.TileType;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Kinematic movement of {@link Mover}s: the motion is split into substeps no longer than about half the mover's size;
 * each substep moves along X and resolves overlaps, then along Y and resolves again. Overlaps are found by SAT and
 * pushed out according to the surface: floors and ceilings (within the floor angle) push vertically, so slopes are
 * climbed at the walking speed; walls push sideways and stop the matching velocity.
 */
final class MoverSolver {

    private static final int MAX_STEPS = 32;
    private static final float SKIN = 0.0005f;

    private final PhysicsWorld physics;
    private final List<Proxy> candidates = new ArrayList<>();
    private final Placed moverPiece = new Placed();
    private final Placed obstacle = new Placed();
    private final Manifold manifold = new Manifold();

    // Deepest overlap found by the last search.
    private float hitDepth;
    private float hitNx;
    private float hitNy;
    private float hitPx;
    private float hitPy;
    private @Nullable Proxy hitProxy;
    private int hitTileX;
    private int hitTileY;
    private boolean hitAny;
    /** Top of the obstacle and bottom of the mover piece of the deepest hit. */
    private float hitTop;

    private float hitBottom;

    // State of the move in progress.
    private Proxy self;
    private Mover mover;
    private float x;
    private float y;
    private float vx;
    private float vy;
    private float stepDx;
    private float stepDy;
    private boolean floor;
    private boolean wall;
    private boolean ceiling;
    private float floorNx;
    private float floorNy;
    private float wallNx;
    private float wallNy;
    private @Nullable Entity floorEntity;
    private float cosFloor;
    private float tanFloor;
    private float landingSpeed;
    private PhysicsAccess.MoveResult out;

    MoverSolver(PhysicsWorld physics) {
        this.physics = physics;
    }

    void move(Proxy proxy, Mover m, float velocityX, float velocityY, PhysicsAccess.MoveResult result) {
        self = proxy;
        mover = m;
        out = result;
        result.contacts.clear();
        MoverState state = proxy.moverState;
        if (state == null) {
            state = new MoverState();
            proxy.moverState = state;
        }
        proxy.refresh();
        Entity entity = proxy.entity;
        float dt = physics.host.tickSeconds();
        boolean wasOnFloor = m.isOnFloor();
        // Ride the platform we stood on.
        Entity platform = state.lastFloor;
        if (m.isPlatformCarry() && platform != null && !platform.isRemoved()) {
            float dx = platform.x() - state.lastFloorX;
            float dy = platform.y() - state.lastFloorY;
            if (dx != 0f || dy != 0f) {
                entity.setPosition(entity.x() + dx, entity.y() + dy);
            }
        }
        x = entity.x();
        y = entity.y();
        vx = velocityX;
        vy = velocityY;
        floor = false;
        wall = false;
        ceiling = false;
        floorEntity = null;
        floorNx = 0f;
        floorNy = -1f;
        wallNx = 0f;
        wallNy = 0f;
        landingSpeed = 0f;
        double floorAngle = Math.toRadians(Math.max(0f, Math.min(89.9f, m.maxFloorAngle())));
        cosFloor = (float) Math.cos(floorAngle);
        tanFloor = (float) Math.tan(floorAngle);
        float motionX = vx * dt;
        float motionY = vy * dt;
        float size = smallestHalfSize(proxy);
        float longest = Math.max(Math.abs(motionX), Math.abs(motionY));
        int steps = Math.max(1, Math.min(MAX_STEPS, (int) Math.ceil(longest / Math.max(0.05f, size * 0.9f))));
        gatherCandidates(proxy, Math.abs(motionX) + Math.abs(motionY) + m.floorSnap() + m.stepHeight() + 1f);
        float dx = motionX / steps;
        float dy = motionY / steps;
        for (int s = 0; s < steps; s++) {
            if (dx != 0f) {
                stepDx = dx;
                stepDy = 0f;
                x += dx;
                resolve(true);
                if (vx == 0f) {
                    dx = 0f;
                }
            }
            if (dy != 0f || s == 0) {
                stepDx = 0f;
                stepDy = dy;
                y += dy;
                resolve(false);
                if (vy == 0f && dy > 0f && floor) {
                    dy = 0f;
                }
                if (vy == 0f && dy < 0f) {
                    dy = 0f;
                }
            }
        }
        // Stay on the ground when walking down slopes or over small gaps.
        if (!m.isTopDown() && wasOnFloor && !floor && vy >= 0f && m.floorSnap() > 0f) {
            float saveY = y;
            float saveVy = vy;
            stepDx = 0f;
            stepDy = m.floorSnap();
            y += m.floorSnap();
            boolean hadFloor = floor;
            resolve(false);
            if (!floor || hadFloor) {
                y = saveY;
                vy = saveVy;
                floor = hadFloor;
            } else {
                vy = Math.max(0f, vy);
            }
        }
        entity.setPosition(x, y);
        proxy.updateBounds(0.1f);
        physics.broadphase.moved(proxy);
        result.velocityX = vx;
        result.velocityY = vy;
        result.floor = floor;
        result.wall = wall;
        result.ceiling = ceiling;
        result.floorNormalX = floorNx;
        result.floorNormalY = floorNy;
        result.wallNormalX = wallNx;
        result.wallNormalY = wallNy;
        result.floorEntity = floorEntity;
        Entity newFloor = floorEntity;
        state.lastFloor = newFloor;
        if (newFloor != null) {
            state.lastFloorX = newFloor.x();
            state.lastFloorY = newFloor.y();
        }
        fireEvents(entity, state, wasOnFloor);
    }

    private static float smallestHalfSize(Proxy proxy) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (Convex piece : proxy.pieces) {
            for (int i = 0; i < piece.count; i++) {
                minX = Math.min(minX, piece.x[i] - piece.radius);
                maxX = Math.max(maxX, piece.x[i] + piece.radius);
                minY = Math.min(minY, piece.y[i] - piece.radius);
                maxY = Math.max(maxY, piece.y[i] + piece.radius);
            }
        }
        return Math.max(0.01f, Math.min(maxX - minX, maxY - minY) * 0.5f);
    }

    private void gatherCandidates(Proxy proxy, float reach) {
        candidates.clear();
        Entity entity = proxy.entity;
        float r = proxy.extent + reach;
        physics.broadphase.query(entity.x() - r, entity.y() - r, entity.x() + r, entity.y() + r, other -> {
            if (other != proxy && !other.pieces.isEmpty() && physics.canTouch(proxy, other)) {
                candidates.add(other);
            }
        });
    }

    /** Pushes the mover out of everything it overlaps, a few times, with rules for the axis just moved. */
    private void resolve(boolean alongX) {
        for (int iteration = 0; iteration < 4; iteration++) {
            findDeepest(alongX);
            if (!hitAny) {
                return;
            }
            push(alongX);
        }
    }

    private void findDeepest(boolean alongX) {
        hitAny = false;
        hitDepth = SKIN;
        for (int p = 0; p < self.pieces.size(); p++) {
            self.placeAt(p, x, y, moverPiece);
            for (int c = 0; c < candidates.size(); c++) {
                Proxy other = candidates.get(c);
                for (int q = 0; q < other.pieces.size(); q++) {
                    other.place(q, obstacle);
                    if (!obstacle.overlapsBox(moverPiece.minX, moverPiece.minY, moverPiece.maxX, moverPiece.maxY)) {
                        continue;
                    }
                    consider(other, 0, 0, other.oneWay, alongX);
                }
            }
            if (physics.canTouchTiles(self)) {
                tileAlongX = alongX;
                int x0 = (int) Math.floor(moverPiece.minX);
                int y0 = (int) Math.floor(moverPiece.minY);
                int x1 = (int) Math.floor(moverPiece.maxX);
                int y1 = (int) Math.floor(moverPiece.maxY);
                physics.host.tiles(x0, y0, x1, y1, this::tile);
            }
        }
    }

    private boolean tileAlongX;

    private void tile(int tx, int ty, TileType type, int flags) {
        List<Convex> pieces = physics.tilePieces(type, flags);
        boolean oneWay = type.shape().isOneWay();
        for (int i = 0; i < pieces.size(); i++) {
            obstacle.set(pieces.get(i), tx, ty);
            if (obstacle.overlapsBox(moverPiece.minX, moverPiece.minY, moverPiece.maxX, moverPiece.maxY)) {
                consider(null, tx, ty, oneWay, tileAlongX);
            }
        }
    }

    private void consider(@Nullable Proxy other, int tx, int ty, boolean oneWay, boolean alongX) {
        Collide.collide(obstacle, moverPiece, 0f, manifold);
        if (manifold.count == 0) {
            return;
        }
        float depth = -manifold.minSeparation();
        if (depth <= hitDepth) {
            return;
        }
        float nx = manifold.nx;
        float ny = manifold.ny;
        if (oneWay) {
            // Only from above, while falling, when the mover was above the platform before this step.
            if (alongX
                    || stepDy <= 0f
                    || !mover.isOneWayPlatforms()
                    || mover.isDroppingThrough()
                    || mover.isTopDown()
                    || ny > -0.7f) {
                return;
            }
            float bottomBefore = moverPiece.maxY - stepDy;
            if (bottomBefore > obstacle.minY + 0.02f) {
                return;
            }
        }
        if (other == null && isInternalEdge(tx, ty, nx, ny)) {
            return;
        }
        hitAny = true;
        hitDepth = depth;
        hitNx = nx;
        hitNy = ny;
        int best = 0;
        for (int i = 1; i < manifold.count; i++) {
            if (manifold.separation[i] < manifold.separation[best]) {
                best = i;
            }
        }
        hitPx = manifold.px[best];
        hitPy = manifold.py[best];
        hitProxy = other;
        hitTileX = tx;
        hitTileY = ty;
        hitTop = obstacle.minY;
        hitBottom = moverPiece.maxY;
    }

    /** A face of a full tile that touches another full tile is inside solid ground; hits on it would snag. */
    private boolean isInternalEdge(int tx, int ty, float nx, float ny) {
        if (!physics.host.isFullTile(tx, ty)) {
            return false;
        }
        if (nx < -0.7f) {
            return physics.host.isFullTile(tx - 1, ty);
        }
        if (nx > 0.7f) {
            return physics.host.isFullTile(tx + 1, ty);
        }
        if (ny < -0.7f) {
            return physics.host.isFullTile(tx, ty - 1);
        }
        if (ny > 0.7f) {
            return physics.host.isFullTile(tx, ty + 1);
        }
        return false;
    }

    private void push(boolean alongX) {
        float depth = hitDepth + SKIN;
        float nx = hitNx;
        float ny = hitNy;
        boolean topDown = mover.isTopDown();
        boolean floorLike = !topDown && -ny >= cosFloor;
        boolean ceilingLike = !topDown && ny >= cosFloor;
        if (floorLike) {
            y -= Math.min(depth / -ny, depth * 4f);
            if (!alongX && vy > 0f) {
                landingSpeed = Math.max(landingSpeed, vy);
            }
            if (!alongX || vy >= 0f) {
                markFloor(nx, ny);
            }
            if (vy > 0f) {
                vy = 0f;
            }
        } else if (ceilingLike) {
            y += Math.min(depth / ny, depth * 4f);
            ceiling = true;
            if (vy < 0f) {
                vy = 0f;
            }
        } else if (alongX && !topDown) {
            if (climbLedge()) {
                recordContact(nx, ny);
                return;
            }
            if (tryStepUp(nx)) {
                return;
            }
            float push = depth / Math.max(Math.abs(nx), 0.2f);
            x += nx >= 0f ? push : -push;
            markWall(nx, ny);
            if (vx * nx < 0f) {
                vx = 0f;
            }
        } else {
            x += nx * depth;
            y += ny * depth;
            markWall(nx, ny);
            float into = vx * nx + vy * ny;
            if (into < 0f) {
                vx -= nx * into;
                vy -= ny * into;
            }
        }
        recordContact(nx, ny);
    }

    /**
     * Treats a wall hit as floor when the obstacle's top is barely above the mover's feet, no higher than a floor at the
     * steepest allowed angle would rise over this substep: the corner where a slope meets flat ground.
     */
    private boolean climbLedge() {
        if (!(floor || mover.isOnFloor())) {
            return false;
        }
        float rise = hitBottom - hitTop;
        if (rise <= 0f || rise > Math.abs(stepDx) * tanFloor + 0.01f) {
            return false;
        }
        y -= rise + SKIN;
        markFloor(0f, -1f);
        return true;
    }

    /** Walks up a small step instead of stopping at it. */
    private boolean tryStepUp(float nx) {
        float height = mover.stepHeight();
        if (height <= 0f || !mover.isOnFloor() || Math.abs(nx) < 0.7f) {
            return false;
        }
        float saveY = y;
        y -= height;
        findDeepest(true);
        if (hitAny) {
            y = saveY;
            findDeepest(true);
            return false;
        }
        // Settle back down onto the step in small moves, so the overlap is always shallowest vertically.
        float saveStep = stepDy;
        float lifted = y;
        float increment = Math.min(0.02f, height);
        for (float dropped = 0f; dropped < height + increment; dropped += increment) {
            stepDy = increment;
            y += increment;
            findDeepest(false);
            if (!hitAny) {
                continue;
            }
            if (-hitNy >= cosFloor && y < saveY) {
                y -= (hitDepth + SKIN) / -hitNy;
                markFloor(hitNx, hitNy);
                recordContact(hitNx, hitNy);
                stepDy = saveStep;
                return true;
            }
            break;
        }
        y = saveY;
        stepDy = saveStep;
        findDeepest(true);
        return false;
    }

    private void markFloor(float nx, float ny) {
        floor = true;
        floorNx = nx;
        floorNy = ny;
        Proxy other = hitProxy;
        floorEntity = other != null ? other.entity : null;
    }

    private void markWall(float nx, float ny) {
        wall = true;
        wallNx = nx;
        wallNy = ny;
    }

    private void recordContact(float nx, float ny) {
        MoverState state = self.moverState;
        Proxy other = hitProxy;
        long key = other != null ? other.serial : PhysicsWorld.tileKey(hitTileX, hitTileY);
        if (state == null || !state.touch(key)) {
            return;
        }
        out.contacts.add(new Contact(
                other != null ? other.entity : null,
                other == null ? new GridPos(hitTileX, hitTileY) : null,
                new Vec2(hitPx, hitPy),
                new Vec2(nx, ny)));
        if (other != null && other.body != null) {
            other.body.wake();
        }
    }

    private void fireEvents(Entity entity, MoverState state, boolean wasOnFloor) {
        var events = physics.host.events();
        if (floor && !wasOnFloor && events.hasListeners(EntityLandEvent.class)) {
            events.call(new EntityLandEvent(entity, landingSpeed, new Vec2(floorNx, floorNy)));
        }
        if (events.hasListeners(EntityCollideEvent.class)) {
            for (Contact contact : out.contacts) {
                long key = keyOf(contact);
                if (!state.wasTouching(key)) {
                    events.call(new EntityCollideEvent(
                            entity, contact.entity(), contact.tile(), contact.point(), contact.normal(), 0f));
                    Entity other = contact.entity();
                    if (other != null && !other.isRemoved()) {
                        events.call(new EntityCollideEvent(
                                other,
                                entity,
                                null,
                                contact.point(),
                                contact.normal().scale(-1f),
                                0f));
                    }
                }
            }
        }
        if (events.hasListeners(EntityCollideEndEvent.class)) {
            for (int i = 0; i < state.previousCount; i++) {
                long key = state.previous[i];
                if (!state.touches(key)) {
                    boolean isTile = PhysicsWorld.isTileKey(key);
                    Proxy other = isTile ? null : physics.proxyBySerial((int) key);
                    GridPos tile = isTile ? new GridPos(PhysicsWorld.tileX(key), PhysicsWorld.tileY(key)) : null;
                    events.call(new EntityCollideEndEvent(entity, other != null ? other.entity : null, tile));
                    if (other != null && !other.entity.isRemoved()) {
                        events.call(new EntityCollideEndEvent(other.entity, entity, null));
                    }
                }
            }
        }
        state.swap();
    }

    private static long keyOf(Contact contact) {
        GridPos tile = contact.tile();
        if (tile != null) {
            return PhysicsWorld.tileKey(tile.x(), tile.y());
        }
        Entity other = contact.entity();
        return other == null ? 0L : other.runtimeId();
    }
}
