package dev.gulp.core.physics;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.physics.DistanceJoint;
import dev.gulp.api.physics.Joint;
import dev.gulp.api.physics.MotorJoint;
import dev.gulp.api.physics.MouseJoint;
import dev.gulp.api.physics.PrismaticJoint;
import dev.gulp.api.physics.RevoluteJoint;
import dev.gulp.api.physics.RopeJoint;
import dev.gulp.api.physics.WeldJoint;
import dev.gulp.api.physics.WheelJoint;
import org.jspecify.annotations.Nullable;

/**
 * Joint implementations as soft constraints: each substep measures the current position error and removes it through
 * velocity impulses, softened by a frequency and damping ratio (stiff by default); a relax pass without the error term
 * removes the velocity the correction added. Anchors are stored in each body's frame, relative to its centre of mass.
 */
final class Joints {

    private Joints() {}

    /** Mass scale, impulse scale and bias rate of a soft constraint for one substep. */
    static final class Softness {
        float biasRate;
        float massScale = 1f;
        float impulseScale;

        void set(float hertz, float dampingRatio, float h) {
            if (hertz <= 0f) {
                biasRate = 0f;
                massScale = 1f;
                impulseScale = 0f;
                return;
            }
            float omega = 2f * (float) Math.PI * hertz;
            float a1 = 2f * dampingRatio + h * omega;
            float a2 = h * omega * a1;
            float a3 = 1f / (1f + a2);
            biasRate = omega / a1;
            massScale = a2 * a3;
            impulseScale = a3;
        }
    }

    static final float JOINT_HERTZ = 60f;
    static final float JOINT_DAMPING = 5f;

    abstract static class JointImpl {
        final PhysicsWorld physics;
        final @Nullable BodySim a;
        final BodySim b;
        final Entity entityA;
        final Entity entityB;
        /** Anchors relative to the centres of mass, in each body's unrotated frame; for a missing body, a world point. */
        float localAx;

        float localAy;
        float localBx;
        float localBy;
        float referenceAngle;
        boolean collideConnected;
        boolean valid = true;
        float reaction;
        float rAx;
        float rAy;
        float rBx;
        float rBy;
        final Softness soft = new Softness();

        JointImpl(
                PhysicsWorld physics,
                @Nullable BodySim a,
                BodySim b,
                Entity entityA,
                Entity entityB,
                Vec2 anchorA,
                Vec2 anchorB) {
            this.physics = physics;
            this.a = a;
            this.b = b;
            this.entityA = entityA;
            this.entityB = entityB;
            if (a != null) {
                float dx = anchorA.x() - a.cx;
                float dy = anchorA.y() - a.cy;
                localAx = a.cos * dx + a.sin * dy;
                localAy = -a.sin * dx + a.cos * dy;
            } else {
                localAx = anchorA.x();
                localAy = anchorA.y();
            }
            float dx = anchorB.x() - b.cx;
            float dy = anchorB.y() - b.cy;
            localBx = b.cos * dx + b.sin * dy;
            localBy = -b.sin * dx + b.cos * dy;
            referenceAngle = b.angle - (a != null ? a.angle : 0f);
        }

        /** Updates the lever arms from the current rotations. */
        final void anchors() {
            if (a != null) {
                float c = (float) Math.cos(a.angle);
                float s = (float) Math.sin(a.angle);
                rAx = c * localAx - s * localAy;
                rAy = s * localAx + c * localAy;
            } else {
                rAx = 0f;
                rAy = 0f;
            }
            float c = (float) Math.cos(b.angle);
            float s = (float) Math.sin(b.angle);
            rBx = c * localBx - s * localBy;
            rBy = s * localBx + c * localBy;
        }

        final float pAx() {
            return a != null ? a.cx + rAx : localAx;
        }

        final float pAy() {
            return a != null ? a.cy + rAy : localAy;
        }

        final float mA() {
            return a != null && a.awakeInSolver() ? a.invMass : 0f;
        }

        final float iA() {
            return a != null && a.awakeInSolver() ? a.invI : 0f;
        }

        final float mB() {
            return b.awakeInSolver() ? b.invMass : 0f;
        }

        final float iB() {
            return b.awakeInSolver() ? b.invI : 0f;
        }

        final float vAx() {
            return a != null ? a.vx : 0f;
        }

        final float vAy() {
            return a != null ? a.vy : 0f;
        }

        final float wA() {
            return a != null ? a.w : 0f;
        }

        /** Relative velocity of the anchors, x. */
        final float dvx() {
            return b.vx - b.w * rBy - vAx() + wA() * rAy;
        }

        final float dvy() {
            return b.vy + b.w * rBx - vAy() - wA() * rAx;
        }

        final void applyLinear(float px, float py) {
            BodySim first = a;
            if (first != null) {
                float m = mA();
                float i = iA();
                first.vx -= m * px;
                first.vy -= m * py;
                first.w -= i * (rAx * py - rAy * px);
            }
            float m = mB();
            float i = iB();
            b.vx += m * px;
            b.vy += m * py;
            b.w += i * (rBx * py - rBy * px);
        }

        final void applyAngular(float impulse) {
            BodySim first = a;
            if (first != null) {
                first.w -= iA() * impulse;
            }
            b.w += iB() * impulse;
        }

        final float relativeAngle() {
            return b.angle - (a != null ? a.angle : 0f) - referenceAngle;
        }

        /** Effective mass of a linear constraint along a unit direction. */
        final float axialMass(float ux, float uy) {
            float crA = rAx * uy - rAy * ux;
            float crB = rBx * uy - rBy * ux;
            float k = mA() + mB() + iA() * crA * crA + iB() * crB * crB;
            return k > 0f ? 1f / k : 0f;
        }

        abstract void warmStart();

        abstract void solve(float h, boolean useBias);

        /** Clears the stored impulses when the joint changes a lot. */
        abstract void resetImpulses();

        /** Draws the joint for debugging. */
        void debugAnchors(float[] out) {
            anchors();
            out[0] = pAx();
            out[1] = pAy();
            out[2] = b.cx + rBx;
            out[3] = b.cy + rBy;
        }

        public Entity entityA() {
            return entityA;
        }

        public Entity entityB() {
            return entityB;
        }

        public Vec2 anchorA() {
            anchors();
            return new Vec2(pAx(), pAy());
        }

        public Vec2 anchorB() {
            anchors();
            return new Vec2(b.cx + rBx, b.cy + rBy);
        }

        public boolean collideConnected() {
            return collideConnected;
        }

        public Joint setCollideConnected(boolean value) {
            collideConnected = value;
            return (Joint) this;
        }

        public float reactionForce() {
            return reaction;
        }

        public boolean isValid() {
            return valid;
        }

        public void remove() {
            physics.removeJoint(this);
        }

        void wake() {
            if (a != null) {
                a.wake();
            }
            b.wake();
        }
    }

    /** Solves a 2D point-to-point constraint; shared by revolute, weld and mouse joints. */
    static final class PointPart {
        float impulseX;
        float impulseY;

        void warmStart(JointImpl j) {
            j.applyLinear(impulseX, impulseY);
        }

        /** Removes the gap between the anchors {@code (cx, cy)} (B minus A) and the relative velocity. */
        void solve(JointImpl j, float cx, float cy, Softness soft, boolean useBias, float h, float maxImpulse) {
            float mA = j.mA();
            float mB = j.mB();
            float iA = j.iA();
            float iB = j.iB();
            float k11 = mA + mB + iA * j.rAy * j.rAy + iB * j.rBy * j.rBy;
            float k12 = -iA * j.rAy * j.rAx - iB * j.rBy * j.rBx;
            float k22 = mA + mB + iA * j.rAx * j.rAx + iB * j.rBx * j.rBx;
            float det = k11 * k22 - k12 * k12;
            if (det == 0f) {
                return;
            }
            float biasX = 0f;
            float biasY = 0f;
            float massScale = 1f;
            float impulseScale = 0f;
            if (useBias) {
                biasX = cx * soft.biasRate;
                biasY = cy * soft.biasRate;
                massScale = soft.massScale;
                impulseScale = soft.impulseScale;
            }
            float vx = j.dvx() + biasX;
            float vy = j.dvy() + biasY;
            float inv = 1f / det;
            float solveX = inv * (k22 * vx - k12 * vy);
            float solveY = inv * (k11 * vy - k12 * vx);
            float ix = -massScale * solveX - impulseScale * impulseX;
            float iy = -massScale * solveY - impulseScale * impulseY;
            float newX = impulseX + ix;
            float newY = impulseY + iy;
            if (maxImpulse > 0f) {
                float length = (float) Math.sqrt(newX * newX + newY * newY);
                if (length > maxImpulse) {
                    newX *= maxImpulse / length;
                    newY *= maxImpulse / length;
                }
            }
            ix = newX - impulseX;
            iy = newY - impulseY;
            impulseX = newX;
            impulseY = newY;
            j.applyLinear(ix, iy);
        }
    }

    /** A one-dimensional angular constraint with optional limits and motor, shared by several joints. */
    static float angularMass(JointImpl j) {
        float k = j.iA() + j.iB();
        return k > 0f ? 1f / k : 0f;
    }

    // ------------------------------------------------------------------ distance and rope

    static class DistanceImpl extends JointImpl implements DistanceJoint {
        float length;
        float min;
        float max;
        float hertz;
        float damping;
        float impulse;
        float lowerImpulse;
        float upperImpulse;
        final Softness spring = new Softness();

        DistanceImpl(PhysicsWorld physics, BodySim a, BodySim b, Vec2 anchorA, Vec2 anchorB) {
            super(physics, a, b, a.proxy.entity, b.proxy.entity, anchorA, anchorB);
            length = Math.max(Collide.SLOP, anchorA.distanceTo(anchorB));
            min = length;
            max = length;
        }

        @Override
        public float length() {
            return length;
        }

        @Override
        public DistanceJoint setLength(float value) {
            length = Math.max(Collide.SLOP, value);
            if (hertz <= 0f) {
                min = length;
                max = length;
            }
            wake();
            return this;
        }

        @Override
        public DistanceJoint setRange(float lo, float hi) {
            min = Math.max(0f, Math.min(lo, hi));
            max = Math.max(lo, hi);
            wake();
            return this;
        }

        @Override
        public DistanceJoint setSpring(float frequencyHz, float dampingRatio) {
            hertz = Math.max(0f, frequencyHz);
            damping = Math.max(0f, dampingRatio);
            if (hertz > 0f && min == max) {
                min = 0f;
                max = Float.MAX_VALUE;
            }
            wake();
            return this;
        }

        @Override
        void warmStart() {
            anchors();
            float dx = b.cx + rBx - pAx();
            float dy = b.cy + rBy - pAy();
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < 1e-6f) {
                return;
            }
            float total = impulse + lowerImpulse - upperImpulse;
            applyLinear(total * dx / d, total * dy / d);
        }

        @Override
        void solve(float h, boolean useBias) {
            anchors();
            float dx = b.cx + rBx - pAx();
            float dy = b.cy + rBy - pAy();
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < 1e-6f) {
                return;
            }
            float ux = dx / d;
            float uy = dy / d;
            float mass = axialMass(ux, uy);
            boolean rigid = hertz <= 0f && min == max;
            if (rigid) {
                float c = d - length;
                float bias = 0f;
                float massScale = 1f;
                float impulseScale = 0f;
                if (useBias) {
                    soft.set(JOINT_HERTZ, JOINT_DAMPING, h);
                    bias = soft.biasRate * c;
                    massScale = soft.massScale;
                    impulseScale = soft.impulseScale;
                }
                float vn = ux * dvx() + uy * dvy();
                float step = -massScale * mass * (vn + bias) - impulseScale * impulse;
                impulse += step;
                applyLinear(step * ux, step * uy);
                reaction = Math.abs(impulse) / h;
                return;
            }
            if (hertz > 0f && useBias) {
                spring.set(hertz, damping, h);
                float c = d - length;
                float vn = ux * dvx() + uy * dvy();
                float step = -spring.massScale * mass * (vn + spring.biasRate * c) - spring.impulseScale * impulse;
                impulse += step;
                applyLinear(step * ux, step * uy);
            }
            if (min > 0f || max < Float.MAX_VALUE) {
                soft.set(JOINT_HERTZ, JOINT_DAMPING, h);
                if (min > 0f) {
                    float c = d - min;
                    float bias = c > 0f ? c / h : useBias ? soft.biasRate * c : 0f;
                    float massScale = c > 0f || !useBias ? 1f : soft.massScale;
                    float impulseScale = c > 0f || !useBias ? 0f : soft.impulseScale;
                    float vn = ux * dvx() + uy * dvy();
                    float step = -massScale * mass * (vn + bias) - impulseScale * lowerImpulse;
                    float next = Math.max(0f, lowerImpulse + step);
                    step = next - lowerImpulse;
                    lowerImpulse = next;
                    applyLinear(step * ux, step * uy);
                }
                if (max < Float.MAX_VALUE) {
                    float c = max - d;
                    float bias = c > 0f ? c / h : useBias ? soft.biasRate * c : 0f;
                    float massScale = c > 0f || !useBias ? 1f : soft.massScale;
                    float impulseScale = c > 0f || !useBias ? 0f : soft.impulseScale;
                    float vn = -(ux * dvx() + uy * dvy());
                    float step = -massScale * mass * (vn + bias) - impulseScale * upperImpulse;
                    float next = Math.max(0f, upperImpulse + step);
                    step = next - upperImpulse;
                    upperImpulse = next;
                    applyLinear(-step * ux, -step * uy);
                }
            }
            reaction = Math.abs(impulse + lowerImpulse - upperImpulse) / h;
        }

        @Override
        void resetImpulses() {
            impulse = 0f;
            lowerImpulse = 0f;
            upperImpulse = 0f;
        }
    }

    static final class RopeImpl extends DistanceImpl implements RopeJoint {
        RopeImpl(PhysicsWorld physics, BodySim a, BodySim b, Vec2 anchorA, Vec2 anchorB, float maxLength) {
            super(physics, a, b, anchorA, anchorB);
            length = Math.max(Collide.SLOP, maxLength);
            min = 0f;
            max = length;
        }

        @Override
        public float maxLength() {
            return max;
        }

        @Override
        public RopeJoint setMaxLength(float value) {
            max = Math.max(Collide.SLOP, value);
            length = max;
            wake();
            return this;
        }
    }

    // ------------------------------------------------------------------ revolute and weld

    static final class RevoluteImpl extends JointImpl implements RevoluteJoint {
        final PointPart point = new PointPart();
        boolean limit;
        float lower;
        float upper;
        boolean motor;
        float motorSpeed;
        float maxTorque;
        float motorImpulse;
        float lowerImpulse;
        float upperImpulse;

        RevoluteImpl(PhysicsWorld physics, BodySim a, BodySim b, Vec2 anchor) {
            super(physics, a, b, a.proxy.entity, b.proxy.entity, anchor, anchor);
        }

        @Override
        public float angle() {
            return (float) Math.toDegrees(relativeAngle());
        }

        @Override
        public RevoluteJoint enableLimit(float lowerDegrees, float upperDegrees) {
            limit = true;
            lower = (float) Math.toRadians(Math.min(lowerDegrees, upperDegrees));
            upper = (float) Math.toRadians(Math.max(lowerDegrees, upperDegrees));
            wake();
            return this;
        }

        @Override
        public RevoluteJoint disableLimit() {
            limit = false;
            lowerImpulse = 0f;
            upperImpulse = 0f;
            wake();
            return this;
        }

        @Override
        public RevoluteJoint enableMotor(float degreesPerSecond, float torque) {
            motor = true;
            motorSpeed = (float) Math.toRadians(degreesPerSecond);
            maxTorque = Math.max(0f, torque);
            wake();
            return this;
        }

        @Override
        public RevoluteJoint disableMotor() {
            motor = false;
            motorImpulse = 0f;
            return this;
        }

        @Override
        void warmStart() {
            anchors();
            point.warmStart(this);
            applyAngular(motorImpulse + lowerImpulse - upperImpulse);
        }

        @Override
        void solve(float h, boolean useBias) {
            anchors();
            soft.set(JOINT_HERTZ, JOINT_DAMPING, h);
            float axial = angularMass(this);
            if (motor) {
                float cdot = b.w - wA() - motorSpeed;
                float step = -axial * cdot;
                float old = motorImpulse;
                float maxImpulse = maxTorque * h;
                motorImpulse = Math.max(-maxImpulse, Math.min(maxImpulse, old + step));
                applyAngular(motorImpulse - old);
            }
            if (limit) {
                float angle = relativeAngle();
                lowerImpulse = angularLimit(angle - lower, lowerImpulse, 1f, axial, h, useBias);
                upperImpulse = angularLimit(upper - angle, upperImpulse, -1f, axial, h, useBias);
            }
            anchors();
            float cx = b.cx + rBx - pAx();
            float cy = b.cy + rBy - pAy();
            point.solve(this, cx, cy, soft, useBias, h, 0f);
            reaction = (float) Math.sqrt(point.impulseX * point.impulseX + point.impulseY * point.impulseY) / h;
        }

        /** One side of an angle limit; returns the new accumulated impulse. */
        private float angularLimit(float c, float accumulated, float sign, float axial, float h, boolean useBias) {
            float bias = 0f;
            float massScale = 1f;
            float impulseScale = 0f;
            if (c > 0f) {
                bias = c / h;
            } else if (useBias) {
                bias = soft.biasRate * c;
                massScale = soft.massScale;
                impulseScale = soft.impulseScale;
            }
            float cdot = sign * (b.w - wA());
            float step = -axial * massScale * (cdot + bias) - impulseScale * accumulated;
            float next = Math.max(0f, accumulated + step);
            applyAngular(sign * (next - accumulated));
            return next;
        }

        @Override
        void resetImpulses() {
            point.impulseX = 0f;
            point.impulseY = 0f;
            motorImpulse = 0f;
            lowerImpulse = 0f;
            upperImpulse = 0f;
        }
    }

    static final class WeldImpl extends JointImpl implements WeldJoint {
        final PointPart point = new PointPart();
        final Softness spring = new Softness();
        float hertz;
        float damping;
        float angularImpulse;

        WeldImpl(PhysicsWorld physics, BodySim a, BodySim b, Vec2 anchor) {
            super(physics, a, b, a.proxy.entity, b.proxy.entity, anchor, anchor);
        }

        @Override
        public WeldJoint setSpring(float frequencyHz, float dampingRatio) {
            hertz = Math.max(0f, frequencyHz);
            damping = Math.max(0f, dampingRatio);
            wake();
            return this;
        }

        @Override
        void warmStart() {
            anchors();
            point.warmStart(this);
            applyAngular(angularImpulse);
        }

        @Override
        void solve(float h, boolean useBias) {
            anchors();
            Softness s = soft;
            if (hertz > 0f) {
                spring.set(hertz, damping, h);
                s = spring;
            } else {
                soft.set(JOINT_HERTZ, JOINT_DAMPING, h);
            }
            float axial = angularMass(this);
            float bias = 0f;
            float massScale = 1f;
            float impulseScale = 0f;
            if (useBias || hertz > 0f) {
                bias = s.biasRate * relativeAngle();
                massScale = s.massScale;
                impulseScale = s.impulseScale;
            }
            float cdot = b.w - wA();
            float step = -axial * massScale * (cdot + bias) - impulseScale * angularImpulse;
            angularImpulse += step;
            applyAngular(step);
            anchors();
            float cx = b.cx + rBx - pAx();
            float cy = b.cy + rBy - pAy();
            point.solve(this, cx, cy, s, useBias || hertz > 0f, h, 0f);
            reaction = (float) Math.sqrt(point.impulseX * point.impulseX + point.impulseY * point.impulseY) / h;
        }

        @Override
        void resetImpulses() {
            point.impulseX = 0f;
            point.impulseY = 0f;
            angularImpulse = 0f;
        }
    }

    // ------------------------------------------------------------------ prismatic and wheel

    /** Shared axis handling of the prismatic and wheel joints: the axis is fixed in body A's frame. */
    abstract static class AxisJoint extends JointImpl {
        final float localAxisX;
        final float localAxisY;
        float axisX;
        float axisY;
        float perpImpulse;
        float dX;
        float dY;

        AxisJoint(PhysicsWorld physics, BodySim a, BodySim b, Vec2 anchor, Vec2 axis) {
            super(physics, a, b, a.proxy.entity, b.proxy.entity, anchor, anchor);
            Vec2 unit = axis.lengthSquared() > 0f ? axis.normalized() : Vec2.UP;
            localAxisX = a.cos * unit.x() + a.sin * unit.y();
            localAxisY = -a.sin * unit.x() + a.cos * unit.y();
        }

        /** Updates anchors, the world axis and {@code d}, the vector between the anchors. */
        final void frame() {
            anchors();
            BodySim first = a;
            float c = first != null ? (float) Math.cos(first.angle) : 1f;
            float s = first != null ? (float) Math.sin(first.angle) : 0f;
            axisX = c * localAxisX - s * localAxisY;
            axisY = s * localAxisX + c * localAxisY;
            dX = b.cx + rBx - pAx();
            dY = b.cy + rBy - pAy();
        }

        /** Solves one linear direction {@code u} with the prismatic Jacobian; returns the impulse applied. */
        final float solveLinear(
                float ux,
                float uy,
                float c,
                float accumulated,
                boolean unilateral,
                float h,
                boolean useBias,
                float maxImpulse,
                float targetSpeed) {
            float a1 = (dX + rAx) * uy - (dY + rAy) * ux;
            float a2 = rBx * uy - rBy * ux;
            float k = mA() + mB() + iA() * a1 * a1 + iB() * a2 * a2;
            if (k <= 0f) {
                return accumulated;
            }
            float mass = 1f / k;
            float cdot = ux * (b.vx - vAx()) + uy * (b.vy - vAy()) + a2 * b.w - a1 * wA() - targetSpeed;
            float bias = 0f;
            float massScale = 1f;
            float impulseScale = 0f;
            if (!Float.isNaN(c)) {
                if (unilateral && c > 0f) {
                    bias = c / h;
                } else if (useBias) {
                    bias = soft.biasRate * c;
                    massScale = soft.massScale;
                    impulseScale = soft.impulseScale;
                }
            }
            float step = -massScale * mass * (cdot + bias) - impulseScale * accumulated;
            float next = accumulated + step;
            if (unilateral) {
                next = Math.max(0f, next);
            }
            if (maxImpulse > 0f) {
                next = Math.max(-maxImpulse, Math.min(maxImpulse, next));
            }
            step = next - accumulated;
            applyPrismatic(ux, uy, a1, a2, step);
            return next;
        }

        final void applyPrismatic(float ux, float uy, float a1, float a2, float impulse) {
            BodySim first = a;
            if (first != null) {
                first.vx -= mA() * impulse * ux;
                first.vy -= mA() * impulse * uy;
                first.w -= iA() * impulse * a1;
            }
            b.vx += mB() * impulse * ux;
            b.vy += mB() * impulse * uy;
            b.w += iB() * impulse * a2;
        }

        final void warmLinear(float ux, float uy, float impulse) {
            float a1 = (dX + rAx) * uy - (dY + rAy) * ux;
            float a2 = rBx * uy - rBy * ux;
            applyPrismatic(ux, uy, a1, a2, impulse);
        }
    }

    static final class PrismaticImpl extends AxisJoint implements PrismaticJoint {
        float angularImpulse;
        boolean limit;
        float lower;
        float upper;
        boolean motor;
        float motorSpeed;
        float maxForce;
        float motorImpulse;
        float lowerImpulse;
        float upperImpulse;

        PrismaticImpl(PhysicsWorld physics, BodySim a, BodySim b, Vec2 anchor, Vec2 axis) {
            super(physics, a, b, anchor, axis);
        }

        @Override
        public float translation() {
            frame();
            return axisX * dX + axisY * dY;
        }

        @Override
        public PrismaticJoint enableLimit(float lo, float hi) {
            limit = true;
            lower = Math.min(lo, hi);
            upper = Math.max(lo, hi);
            wake();
            return this;
        }

        @Override
        public PrismaticJoint disableLimit() {
            limit = false;
            lowerImpulse = 0f;
            upperImpulse = 0f;
            wake();
            return this;
        }

        @Override
        public PrismaticJoint enableMotor(float speed, float force) {
            motor = true;
            motorSpeed = speed;
            maxForce = Math.max(0f, force);
            wake();
            return this;
        }

        @Override
        public PrismaticJoint disableMotor() {
            motor = false;
            motorImpulse = 0f;
            return this;
        }

        @Override
        void warmStart() {
            frame();
            warmLinear(-axisY, axisX, perpImpulse);
            warmLinear(axisX, axisY, motorImpulse + lowerImpulse - upperImpulse);
            applyAngular(angularImpulse);
        }

        @Override
        void solve(float h, boolean useBias) {
            soft.set(JOINT_HERTZ, JOINT_DAMPING, h);
            frame();
            if (motor) {
                motorImpulse =
                        solveLinear(axisX, axisY, Float.NaN, motorImpulse, false, h, false, maxForce * h, motorSpeed);
            }
            if (limit) {
                float translation = axisX * dX + axisY * dY;
                lowerImpulse = solveLinear(axisX, axisY, translation - lower, lowerImpulse, true, h, useBias, 0f, 0f);
                frame();
                upperImpulse = solveLinear(-axisX, -axisY, upper - translation, upperImpulse, true, h, useBias, 0f, 0f);
            }
            // Keep the angle.
            float axial = angularMass(this);
            float bias = 0f;
            float massScale = 1f;
            float impulseScale = 0f;
            if (useBias) {
                bias = soft.biasRate * relativeAngle();
                massScale = soft.massScale;
                impulseScale = soft.impulseScale;
            }
            float step = -axial * massScale * (b.w - wA() + bias) - impulseScale * angularImpulse;
            angularImpulse += step;
            applyAngular(step);
            // Stay on the axis.
            frame();
            float perpX = -axisY;
            float perpY = axisX;
            perpImpulse = solveLinear(perpX, perpY, perpX * dX + perpY * dY, perpImpulse, false, h, useBias, 0f, 0f);
            reaction = Math.abs(perpImpulse) / h;
        }

        @Override
        void resetImpulses() {
            perpImpulse = 0f;
            angularImpulse = 0f;
            motorImpulse = 0f;
            lowerImpulse = 0f;
            upperImpulse = 0f;
        }
    }

    static final class WheelImpl extends AxisJoint implements WheelJoint {
        final Softness spring = new Softness();
        float hertz = 4f;
        float damping = 0.7f;
        float springImpulse;
        boolean motor;
        float motorSpeed;
        float maxTorque;
        float motorImpulse;

        WheelImpl(PhysicsWorld physics, BodySim a, BodySim b, Vec2 anchor, Vec2 axis) {
            super(physics, a, b, anchor, axis);
        }

        @Override
        public WheelJoint setSpring(float frequencyHz, float dampingRatio) {
            hertz = Math.max(0f, frequencyHz);
            damping = Math.max(0f, dampingRatio);
            wake();
            return this;
        }

        @Override
        public WheelJoint enableMotor(float degreesPerSecond, float torque) {
            motor = true;
            motorSpeed = (float) Math.toRadians(degreesPerSecond);
            maxTorque = Math.max(0f, torque);
            wake();
            return this;
        }

        @Override
        public WheelJoint disableMotor() {
            motor = false;
            motorImpulse = 0f;
            return this;
        }

        @Override
        void warmStart() {
            frame();
            warmLinear(-axisY, axisX, perpImpulse);
            warmLinear(axisX, axisY, springImpulse);
            applyAngular(motorImpulse);
        }

        @Override
        void solve(float h, boolean useBias) {
            soft.set(JOINT_HERTZ, JOINT_DAMPING, h);
            frame();
            if (motor) {
                float axial = angularMass(this);
                float step = -axial * (b.w - wA() - motorSpeed);
                float old = motorImpulse;
                float maxImpulse = maxTorque * h;
                motorImpulse = Math.max(-maxImpulse, Math.min(maxImpulse, old + step));
                applyAngular(motorImpulse - old);
            }
            if (hertz > 0f) {
                // The suspension spring along the axis, towards the rest position.
                spring.set(hertz, damping, h);
                float translation = axisX * dX + axisY * dY;
                float a1 = (dX + rAx) * axisY - (dY + rAy) * axisX;
                float a2 = rBx * axisY - rBy * axisX;
                float k = mA() + mB() + iA() * a1 * a1 + iB() * a2 * a2;
                if (k > 0f && useBias) {
                    float cdot = axisX * (b.vx - vAx()) + axisY * (b.vy - vAy()) + a2 * b.w - a1 * wA();
                    float step = -spring.massScale / k * (cdot + spring.biasRate * translation)
                            - spring.impulseScale * springImpulse;
                    springImpulse += step;
                    applyPrismatic(axisX, axisY, a1, a2, step);
                }
            } else {
                springImpulse =
                        solveLinear(axisX, axisY, axisX * dX + axisY * dY, springImpulse, false, h, useBias, 0f, 0f);
            }
            frame();
            float perpX = -axisY;
            float perpY = axisX;
            perpImpulse = solveLinear(perpX, perpY, perpX * dX + perpY * dY, perpImpulse, false, h, useBias, 0f, 0f);
            reaction = Math.abs(perpImpulse) / h;
        }

        @Override
        void resetImpulses() {
            perpImpulse = 0f;
            springImpulse = 0f;
            motorImpulse = 0f;
        }
    }

    // ------------------------------------------------------------------ mouse and motor

    static final class MouseImpl extends JointImpl implements MouseJoint {
        final PointPart point = new PointPart();
        final Softness spring = new Softness();
        float targetX;
        float targetY;
        float maxForce;
        float hertz = 5f;
        float damping = 0.7f;
        float angularImpulse;

        MouseImpl(PhysicsWorld physics, BodySim b, Vec2 target) {
            super(physics, null, b, b.proxy.entity, b.proxy.entity, target, target);
            targetX = target.x();
            targetY = target.y();
            maxForce = 1000f * Math.max(b.mass, 1f);
        }

        @Override
        public Vec2 target() {
            return new Vec2(targetX, targetY);
        }

        @Override
        public MouseJoint setTarget(Vec2 value) {
            targetX = value.x();
            targetY = value.y();
            b.wake();
            return this;
        }

        @Override
        public MouseJoint setMaxForce(float value) {
            maxForce = Math.max(0f, value);
            return this;
        }

        @Override
        public MouseJoint setSpring(float frequencyHz, float dampingRatio) {
            hertz = Math.max(0.01f, frequencyHz);
            damping = Math.max(0f, dampingRatio);
            return this;
        }

        @Override
        void warmStart() {
            anchors();
            point.warmStart(this);
            applyAngular(angularImpulse);
        }

        @Override
        void solve(float h, boolean useBias) {
            anchors();
            spring.set(hertz, damping, h);
            // A little angular damping keeps a dragged body from spinning forever.
            float axial = angularMass(this);
            float step = -axial * 0.02f * b.w;
            angularImpulse += step;
            applyAngular(step);
            float cx = b.cx + rBx - targetX;
            float cy = b.cy + rBy - targetY;
            point.solve(this, cx, cy, spring, true, h, maxForce * h);
            reaction = (float) Math.sqrt(point.impulseX * point.impulseX + point.impulseY * point.impulseY) / h;
        }

        @Override
        void debugAnchors(float[] out) {
            anchors();
            out[0] = targetX;
            out[1] = targetY;
            out[2] = b.cx + rBx;
            out[3] = b.cy + rBy;
        }

        @Override
        void resetImpulses() {
            point.impulseX = 0f;
            point.impulseY = 0f;
            angularImpulse = 0f;
        }
    }

    static final class MotorImpl extends JointImpl implements MotorJoint {
        float offsetX;
        float offsetY;
        float angularOffset;
        float maxForce = 1000f;
        float maxTorque = 1000f;
        float correction = 0.3f;
        float linearX;
        float linearY;
        float angular;

        MotorImpl(PhysicsWorld physics, BodySim a, BodySim b) {
            super(physics, a, b, a.proxy.entity, b.proxy.entity, new Vec2(a.cx, a.cy), new Vec2(b.cx, b.cy));
            float dx = b.cx - a.cx;
            float dy = b.cy - a.cy;
            offsetX = a.cos * dx + a.sin * dy;
            offsetY = -a.sin * dx + a.cos * dy;
            angularOffset = b.angle - a.angle;
        }

        @Override
        public MotorJoint setLinearOffset(Vec2 offset) {
            offsetX = offset.x();
            offsetY = offset.y();
            wake();
            return this;
        }

        @Override
        public MotorJoint setAngularOffset(float degrees) {
            angularOffset = (float) Math.toRadians(degrees);
            wake();
            return this;
        }

        @Override
        public MotorJoint setMaxForce(float value) {
            maxForce = Math.max(0f, value);
            return this;
        }

        @Override
        public MotorJoint setMaxTorque(float value) {
            maxTorque = Math.max(0f, value);
            return this;
        }

        @Override
        public MotorJoint setCorrectionFactor(float value) {
            correction = Math.max(0f, Math.min(1f, value));
            return this;
        }

        @Override
        void warmStart() {
            rAx = 0f;
            rAy = 0f;
            rBx = 0f;
            rBy = 0f;
            applyLinear(linearX, linearY);
            applyAngular(angular);
        }

        @Override
        void solve(float h, boolean useBias) {
            BodySim first = a;
            if (first == null) {
                return;
            }
            rAx = 0f;
            rAy = 0f;
            rBx = 0f;
            rBy = 0f;
            float inv = 1f / h;
            float axial = angularMass(this);
            // The motor drives towards its target in both passes; it is a velocity goal, not a rigid constraint.
            float angleError = b.angle - first.angle - angularOffset;
            float angularBias = correction * inv * angleError;
            float step = -axial * (b.w - first.w + angularBias);
            float old = angular;
            float maxAngular = maxTorque * h;
            angular = Math.max(-maxAngular, Math.min(maxAngular, old + step));
            applyAngular(angular - old);
            float c = (float) Math.cos(first.angle);
            float s = (float) Math.sin(first.angle);
            float targetX = first.cx + c * offsetX - s * offsetY;
            float targetY = first.cy + s * offsetX + c * offsetY;
            float ex = b.cx - targetX;
            float ey = b.cy - targetY;
            float k = mA() + mB();
            if (k <= 0f) {
                return;
            }
            float vx = b.vx - first.vx + correction * inv * ex;
            float vy = b.vy - first.vy + correction * inv * ey;
            float ix = -vx / k;
            float iy = -vy / k;
            float nextX = linearX + ix;
            float nextY = linearY + iy;
            float length = (float) Math.sqrt(nextX * nextX + nextY * nextY);
            float maxLinear = maxForce * h;
            if (length > maxLinear) {
                nextX *= maxLinear / length;
                nextY *= maxLinear / length;
            }
            applyLinear(nextX - linearX, nextY - linearY);
            linearX = nextX;
            linearY = nextY;
            reaction = (float) Math.sqrt(linearX * linearX + linearY * linearY) / h;
        }

        @Override
        void resetImpulses() {
            linearX = 0f;
            linearY = 0f;
            angular = 0f;
        }
    }
}
