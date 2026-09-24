package dev.gulp.api.physics;

import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.spi.PhysicsAccess;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Moves a character with one call per tick, sliding along walls and slopes, like Godot's {@code CharacterBody2D}. The
 * shape, layer and mask come from the entity's {@link Collider}, or a box of the entity's size. Collision tiles, colliders
 * and bodies block it; other movers too, when the layers allow.
 *
 * <p>{@link #moveAndSlide} adds gravity (unless {@link #topDown}), moves in substeps so that fast movement does not pass
 * through walls, resolves the X and Y axes separately with SAT and updates {@link #velocity()}: it drops the part of
 * the velocity that ran into something.
 *
 * <pre>{@code
 * float x = input().axis(LEFT, RIGHT) * 7f;
 * if (input().justPressed(JUMP) && mover.canJump()) { mover.jump(12f); }
 * mover.moveAndSlide(mover.velocity().withX(x));
 * }</pre>
 */
public final class Mover extends Component {

    private Vec2 velocity = Vec2.ZERO;
    private boolean gravity = true;
    private boolean topDown;
    private float maxFloorAngle = 46f;
    private float floorSnap = 0.2f;
    private float stepHeight;
    private boolean platformCarry = true;
    private boolean oneWayPlatforms = true;
    private int coyoteTicks;
    private int jumpBufferTicks;

    private boolean onFloor;
    private boolean onWall;
    private boolean onCeiling;
    private Vec2 floorNormal = Vec2.UP;
    private Vec2 wallNormal = Vec2.ZERO;
    private @Nullable Entity floorEntity;
    private int airTicks = Integer.MAX_VALUE / 2;
    private boolean jumpedSinceFloor;
    private int bufferedTicks;
    private float bufferedSpeed;
    private int dropTicks;
    private final PhysicsAccess.MoveResult result = new PhysicsAccess.MoveResult();
    private final List<Contact> contacts = Collections.unmodifiableList(result.contacts);

    /** Creates a mover with gravity. */
    public Mover() {}

    /**
     * Moves the entity by {@code velocity} for one tick and slides along what it hits.
     *
     * @param velocity the wanted velocity in units per second; gravity is added to it
     * @return the contacts of this move, valid until the next move
     */
    public List<Contact> moveAndSlide(Vec2 velocity) {
        float dt = 1f / engine().targetTps();
        float vx = velocity.x();
        float vy = velocity.y();
        if (gravity && !topDown) {
            Vec2 g = world().physics().gravity();
            vx += g.x() * dt;
            vy += g.y() * dt;
        }
        PhysicsAccess.backend().moveAndSlide(this, vx, vy, result);
        this.velocity = new Vec2(result.velocityX, result.velocityY);
        onFloor = result.floor && !topDown;
        onWall = result.wall;
        onCeiling = result.ceiling && !topDown;
        floorNormal = onFloor ? new Vec2(result.floorNormalX, result.floorNormalY) : Vec2.UP;
        wallNormal = onWall ? new Vec2(result.wallNormalX, result.wallNormalY) : Vec2.ZERO;
        floorEntity = onFloor ? result.floorEntity : null;
        if (dropTicks > 0) {
            dropTicks--;
        }
        if (onFloor) {
            airTicks = 0;
            jumpedSinceFloor = false;
            if (bufferedTicks > 0) {
                bufferedTicks = 0;
                jump(bufferedSpeed);
            }
        } else {
            airTicks++;
            if (bufferedTicks > 0) {
                bufferedTicks--;
            }
        }
        return contacts;
    }

    /**
     * Returns the velocity after the last move.
     *
     * @return units per second
     */
    public Vec2 velocity() {
        return velocity;
    }

    /**
     * Replaces the velocity, for example to knock the character back.
     *
     * @param value units per second
     * @return this mover
     */
    public Mover setVelocity(Vec2 value) {
        velocity = value;
        return this;
    }

    /**
     * Returns whether the last move ended on a floor.
     *
     * @return {@code true} on a floor
     */
    public boolean isOnFloor() {
        return onFloor;
    }

    /**
     * Returns whether the last move touched a wall.
     *
     * @return {@code true} at a wall
     */
    public boolean isOnWall() {
        return onWall;
    }

    /**
     * Returns whether the last move hit a ceiling.
     *
     * @return {@code true} under a ceiling
     */
    public boolean isOnCeiling() {
        return onCeiling;
    }

    /**
     * Returns the normal of the floor.
     *
     * @return the normal, {@link Vec2#UP} when not on a floor
     */
    public Vec2 floorNormal() {
        return floorNormal;
    }

    /**
     * Returns the normal of the wall.
     *
     * @return the normal, {@link Vec2#ZERO} when not at a wall
     */
    public Vec2 wallNormal() {
        return wallNormal;
    }

    /**
     * Returns the entity the mover stands on, such as a moving platform.
     *
     * @return the entity, or {@code null} on tiles or in the air
     */
    public @Nullable Entity floorEntity() {
        return floorEntity;
    }

    /**
     * Returns the contacts of the last move.
     *
     * @return a live view, replaced by the next move
     */
    public List<Contact> contacts() {
        return contacts;
    }

    /**
     * Returns whether a jump is allowed now: on a floor, or within the coyote time after walking off one.
     *
     * @return {@code true} if {@link #jump} would jump
     */
    public boolean canJump() {
        return onFloor || (!jumpedSinceFloor && airTicks <= coyoteTicks);
    }

    /**
     * Jumps if {@link #canJump()}; otherwise remembers the jump for the jump buffer time and jumps on landing.
     *
     * @param speed upward speed in units per second
     * @return {@code true} if it jumped now
     */
    public boolean jump(float speed) {
        if (canJump()) {
            velocity = new Vec2(velocity.x(), -speed);
            jumpedSinceFloor = true;
            onFloor = false;
            airTicks = Integer.MAX_VALUE / 2;
            bufferedTicks = 0;
            return true;
        }
        if (jumpBufferTicks > 0) {
            bufferedTicks = jumpBufferTicks;
            bufferedSpeed = speed;
        }
        return false;
    }

    /** Falls through the one-way platform underneath for a few ticks. */
    public void dropThroughPlatform() {
        dropTicks = 8;
        onFloor = false;
    }

    /**
     * Returns whether the mover is currently dropping through one-way platforms.
     *
     * @return {@code true} for a few ticks after {@link #dropThroughPlatform()}
     */
    public boolean isDroppingThrough() {
        return dropTicks > 0;
    }

    /**
     * Returns whether gravity is added.
     *
     * @return {@code true} by default
     */
    public boolean hasGravity() {
        return gravity;
    }

    /**
     * Turns gravity on or off.
     *
     * @param value whether to add the world gravity
     * @return this mover
     */
    public Mover gravity(boolean value) {
        gravity = value;
        return this;
    }

    /**
     * Returns whether the mover works top-down.
     *
     * @return {@code true} without gravity and floors
     */
    public boolean isTopDown() {
        return topDown;
    }

    /**
     * Switches to top-down movement: no gravity, no floors or ceilings, everything is a wall.
     *
     * @param value whether top-down
     * @return this mover
     */
    public Mover topDown(boolean value) {
        topDown = value;
        return this;
    }

    /**
     * Returns the steepest slope that still counts as a floor.
     *
     * @return degrees, {@code 46} by default
     */
    public float maxFloorAngle() {
        return maxFloorAngle;
    }

    /**
     * Sets the steepest slope that still counts as a floor.
     *
     * @param degrees the angle
     * @return this mover
     */
    public Mover maxFloorAngle(float degrees) {
        maxFloorAngle = degrees;
        return this;
    }

    /**
     * Returns how far the mover snaps down to keep walking on a floor, for example down a slope.
     *
     * @return world units
     */
    public float floorSnap() {
        return floorSnap;
    }

    /**
     * Sets how far the mover snaps down to stay on a floor; {@code 0} turns snapping off.
     *
     * @param distance world units
     * @return this mover
     */
    public Mover floorSnap(float distance) {
        floorSnap = distance;
        return this;
    }

    /**
     * Returns the highest step the mover walks up without jumping.
     *
     * @return world units, {@code 0} by default
     */
    public float stepHeight() {
        return stepHeight;
    }

    /**
     * Sets the highest step the mover walks up without jumping.
     *
     * @param height world units
     * @return this mover
     */
    public Mover stepHeight(float height) {
        stepHeight = height;
        return this;
    }

    /**
     * Returns whether moving platforms carry the mover.
     *
     * @return {@code true} by default
     */
    public boolean isPlatformCarry() {
        return platformCarry;
    }

    /**
     * Sets whether the entity under the mover carries it along when it moves.
     *
     * @param value whether carried
     * @return this mover
     */
    public Mover platformCarry(boolean value) {
        platformCarry = value;
        return this;
    }

    /**
     * Returns whether one-way platforms block from above.
     *
     * @return {@code true} by default
     */
    public boolean isOneWayPlatforms() {
        return oneWayPlatforms;
    }

    /**
     * Sets whether one-way platforms (tiles and colliders) hold the mover; when off, it passes through them.
     *
     * @param value whether they block
     * @return this mover
     */
    public Mover oneWayPlatforms(boolean value) {
        oneWayPlatforms = value;
        return this;
    }

    /**
     * Returns the coyote time.
     *
     * @return ticks
     */
    public int coyoteTicks() {
        return coyoteTicks;
    }

    /**
     * Allows jumping for a few ticks after walking off a ledge.
     *
     * @param ticks the grace time
     * @return this mover
     */
    public Mover coyoteTicks(int ticks) {
        coyoteTicks = ticks;
        return this;
    }

    /**
     * Returns the jump buffer time.
     *
     * @return ticks
     */
    public int jumpBufferTicks() {
        return jumpBufferTicks;
    }

    /**
     * Remembers a jump pressed shortly before landing and performs it on landing.
     *
     * @param ticks how long a jump is remembered
     * @return this mover
     */
    public Mover jumpBufferTicks(int ticks) {
        jumpBufferTicks = ticks;
        return this;
    }

    @Override
    protected void onSpawn() {
        PhysicsAccess.backend().attach(this);
    }

    @Override
    protected void onRemove() {
        PhysicsAccess.backend().detach(this);
    }
}
