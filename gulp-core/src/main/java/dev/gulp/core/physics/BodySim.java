package dev.gulp.core.physics;

import dev.gulp.api.physics.Body;
import dev.gulp.api.physics.BodyType;
import dev.gulp.api.spi.PhysicsAccess;

/**
 * The simulated state of a {@link Body}: centre of mass, velocities, forces and sleep. Positions are of the centre of
 * mass; the entity origin is derived from it.
 */
final class BodySim implements PhysicsAccess.BodyHandle {

    final Body body;
    final Proxy proxy;
    final PhysicsWorld physics;
    BodyType type;
    boolean massChanged = true;

    float mass;
    float invMass;
    float inertia;
    float invI;
    /** Centre of mass in entity coordinates (unrotated). */
    float localCx;

    float localCy;

    float cx;
    float cy;
    float angle;
    float cos = 1f;
    float sin;
    float cx0;
    float cy0;
    float angle0;
    float vx;
    float vy;
    float w;
    float fx;
    float fy;
    float torque;

    boolean awake = true;
    float sleepTime;
    int island;
    boolean onFloor;
    float writtenX = Float.NaN;
    float writtenY = Float.NaN;
    float writtenRotation = Float.NaN;

    BodySim(PhysicsWorld physics, Body body, Proxy proxy) {
        this.physics = physics;
        this.body = body;
        this.proxy = proxy;
        this.type = body.type();
    }

    /** Whether the solver may change this body's velocity: awake dynamic bodies; others have no mass anyway. */
    boolean awakeInSolver() {
        return awake || type != BodyType.DYNAMIC;
    }

    boolean isDynamic() {
        return type == BodyType.DYNAMIC;
    }

    /** Recomputes mass and centre from the pieces, keeping the entity where it is. */
    void updateMass() {
        massChanged = false;
        type = body.type();
        float area = 0f;
        float mx = 0f;
        float my = 0f;
        float rotational = 0f;
        for (Convex piece : proxy.pieces) {
            area += piece.area;
            mx += piece.area * piece.centroidX;
            my += piece.area * piece.centroidY;
        }
        if (area > 0f) {
            localCx = mx / area;
            localCy = my / area;
        } else {
            localCx = 0f;
            localCy = 0f;
        }
        for (Convex piece : proxy.pieces) {
            float dx = piece.centroidX - localCx;
            float dy = piece.centroidY - localCy;
            rotational += piece.inertia + piece.area * (dx * dx + dy * dy);
        }
        if (type != BodyType.DYNAMIC) {
            mass = 0f;
            invMass = 0f;
            inertia = 0f;
            invI = 0f;
        } else {
            float fixed = body.fixedMass();
            float density = body.density();
            mass = fixed > 0f ? fixed : area * density;
            if (!(mass > 0f)) {
                mass = 1f;
            }
            float scale = area > 0f ? mass / area : 1f;
            inertia = rotational * scale;
            invMass = 1f / mass;
            invI = body.isFixedRotation() || inertia <= 0f ? 0f : 1f / inertia;
            if (body.isFixedRotation()) {
                w = 0f;
            }
        }
        syncFromEntity();
    }

    /** Places the centre of mass from the entity's position and rotation. */
    void syncFromEntity() {
        angle = (float) Math.toRadians(proxy.entity.rotation());
        cos = (float) Math.cos(angle);
        sin = (float) Math.sin(angle);
        cx = proxy.entity.x() + cos * localCx - sin * localCy;
        cy = proxy.entity.y() + sin * localCx + cos * localCy;
        writtenX = proxy.entity.x();
        writtenY = proxy.entity.y();
        writtenRotation = proxy.entity.rotation();
    }

    /** Returns whether game code moved the entity since the physics last wrote it. */
    boolean movedByGame() {
        return proxy.entity.x() != writtenX
                || proxy.entity.y() != writtenY
                || proxy.entity.rotation() != writtenRotation;
    }

    /** Writes the centre of mass back as the entity's position and rotation. */
    void writeToEntity() {
        cos = (float) Math.cos(angle);
        sin = (float) Math.sin(angle);
        float ox = cx - (cos * localCx - sin * localCy);
        float oy = cy - (sin * localCx + cos * localCy);
        float degrees = (float) Math.toDegrees(angle);
        proxy.entity.setPosition(ox, oy);
        if (proxy.entity.rotation() != degrees) {
            proxy.entity.setRotation(degrees);
        }
        writtenX = proxy.entity.x();
        writtenY = proxy.entity.y();
        writtenRotation = proxy.entity.rotation();
    }

    void wake() {
        if (!awake) {
            awake = true;
            sleepTime = 0f;
        }
    }

    // ------------------------------------------------------------------ BodyHandle

    @Override
    public float velocityX() {
        return vx;
    }

    @Override
    public float velocityY() {
        return vy;
    }

    @Override
    public float angularVelocity() {
        return (float) Math.toDegrees(w);
    }

    @Override
    public void setVelocity(float x, float y) {
        if (type == BodyType.STATIC) {
            return;
        }
        vx = x;
        vy = y;
        if (x != 0f || y != 0f) {
            wake();
        }
    }

    @Override
    public void setAngularVelocity(float degreesPerSecond) {
        if (type == BodyType.STATIC || body.isFixedRotation()) {
            return;
        }
        w = (float) Math.toRadians(degreesPerSecond);
        if (w != 0f) {
            wake();
        }
    }

    @Override
    public void applyForce(float forceX, float forceY, float px, float py) {
        if (type != BodyType.DYNAMIC) {
            return;
        }
        fx += forceX;
        fy += forceY;
        if (!Float.isNaN(px)) {
            torque += (px - cx) * forceY - (py - cy) * forceX;
        }
        wake();
    }

    @Override
    public void applyImpulse(float ix, float iy, float px, float py) {
        if (type != BodyType.DYNAMIC) {
            return;
        }
        if (massChanged) {
            updateMass();
        }
        vx += invMass * ix;
        vy += invMass * iy;
        if (!Float.isNaN(px)) {
            w += invI * ((px - cx) * iy - (py - cy) * ix);
        }
        wake();
    }

    @Override
    public void applyTorque(float value) {
        if (type != BodyType.DYNAMIC) {
            return;
        }
        torque += value;
        wake();
    }

    @Override
    public boolean isAwake() {
        return awake;
    }

    @Override
    public void setAwake(boolean value) {
        if (value) {
            wake();
        } else if (type == BodyType.DYNAMIC) {
            awake = false;
            vx = 0f;
            vy = 0f;
            w = 0f;
        }
    }

    @Override
    public float mass() {
        if (massChanged) {
            proxy.refresh();
            updateMass();
        }
        return mass;
    }

    @Override
    public void refresh() {
        massChanged = true;
        wake();
        physics.bodyChanged(this);
    }
}
