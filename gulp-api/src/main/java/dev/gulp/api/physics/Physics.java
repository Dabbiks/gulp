package dev.gulp.api.physics;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The physics of one world: gravity, the collision matrix, queries and joints. Queries see collision tiles, colliders,
 * movers and bodies (not triggers) and work with both the kinematic and the rigid body level.
 *
 * <pre>{@code
 * Physics physics = world.physics();
 * physics.setCollides(PLAYER, PICKUP, true);
 * RayHit ground = physics.raycast(feet, feet.add(0, 2), CollisionMask.of(CollisionLayer.TILES));
 * List<Entity> blast = physics.overlapCircle(bomb.position(), 3f, CollisionMask.ALL);
 * }</pre>
 */
public interface Physics {

    /**
     * Returns the gravity.
     *
     * @return units per second squared, from {@code WorldSettings} at first
     */
    Vec2 gravity();

    /**
     * Changes the gravity for movers and bodies.
     *
     * @param value units per second squared; Y points down
     */
    void setGravity(Vec2 value);

    /**
     * Returns the number of rigid body substeps per tick.
     *
     * @return {@code 4} by default
     */
    int subSteps();

    /**
     * Sets the number of rigid body substeps per tick: more is stiffer and slower.
     *
     * @param value {@code 1..16}
     */
    void setSubSteps(int value);

    /**
     * Allows or forbids contacts between two layers, on top of the colliders' own masks.
     *
     * @param a one layer
     * @param b the other layer
     * @param collide whether they may touch
     */
    void setCollides(CollisionLayer a, CollisionLayer b, boolean collide);

    /**
     * Returns whether the matrix allows two layers to touch.
     *
     * @param a one layer
     * @param b the other layer
     * @return {@code true} by default
     */
    boolean collides(CollisionLayer a, CollisionLayer b);

    /**
     * Casts a ray against everything.
     *
     * @param from the start
     * @param to the end
     * @return the first hit, or {@code null}
     */
    @Nullable RayHit raycast(Vec2 from, Vec2 to);

    /**
     * Casts a ray against the given layers.
     *
     * @param from the start
     * @param to the end
     * @param mask the layers to hit
     * @return the first hit, or {@code null}
     */
    @Nullable RayHit raycast(Vec2 from, Vec2 to, CollisionMask mask);

    /**
     * Casts a ray and returns every hit, nearest first.
     *
     * @param from the start
     * @param to the end
     * @param mask the layers to hit
     * @return the hits, one per entity or tile
     */
    List<RayHit> raycastAll(Vec2 from, Vec2 to, CollisionMask mask);

    /**
     * Sweeps a shape along a line and returns the first thing it touches.
     *
     * @param shape the shape, without rotation
     * @param from the start position of the shape
     * @param to the end position
     * @param mask the layers to hit
     * @return the first hit; its point lies on the obstacle, or {@code null}
     */
    @Nullable RayHit shapeCast(Shape shape, Vec2 from, Vec2 to, CollisionMask mask);

    /**
     * Returns the entities whose shape contains a point.
     *
     * @param point the point
     * @param mask the layers to consider
     * @return the entities
     */
    List<Entity> overlapPoint(Vec2 point, CollisionMask mask);

    /**
     * Returns the entities whose shape overlaps a circle.
     *
     * @param center the centre
     * @param radius the radius
     * @param mask the layers to consider
     * @return the entities
     */
    List<Entity> overlapCircle(Vec2 center, float radius, CollisionMask mask);

    /**
     * Returns the entities whose shape overlaps a rectangle.
     *
     * @param area the rectangle
     * @param mask the layers to consider
     * @return the entities
     */
    List<Entity> overlapRect(Rect area, CollisionMask mask);

    /**
     * Returns whether a point lies inside a collision tile.
     *
     * @param point the point
     * @return {@code true} inside solid tile geometry
     */
    boolean isSolidTile(Vec2 point);

    /**
     * Keeps two anchors at their current distance.
     *
     * @param a first entity with a {@link Body}
     * @param b second entity with a {@link Body}
     * @param anchorA anchor on {@code a}, world units
     * @param anchorB anchor on {@code b}, world units
     * @return the joint
     */
    DistanceJoint distance(Entity a, Entity b, Vec2 anchorA, Vec2 anchorB);

    /**
     * Keeps two anchors at most {@code maxLength} apart.
     *
     * @param a first entity with a {@link Body}
     * @param b second entity with a {@link Body}
     * @param anchorA anchor on {@code a}
     * @param anchorB anchor on {@code b}
     * @param maxLength the rope length
     * @return the joint
     */
    RopeJoint rope(Entity a, Entity b, Vec2 anchorA, Vec2 anchorB, float maxLength);

    /**
     * Pins two bodies at a point they turn around.
     *
     * @param a first entity with a {@link Body}
     * @param b second entity with a {@link Body}
     * @param anchor the pivot, world units
     * @return the joint
     */
    RevoluteJoint revolute(Entity a, Entity b, Vec2 anchor);

    /**
     * Lets {@code b} slide along an axis of {@code a}.
     *
     * @param a first entity with a {@link Body}
     * @param b second entity with a {@link Body}
     * @param anchor a point on the axis, world units
     * @param axis the direction of the axis
     * @return the joint
     */
    PrismaticJoint prismatic(Entity a, Entity b, Vec2 anchor, Vec2 axis);

    /**
     * Glues two bodies together.
     *
     * @param a first entity with a {@link Body}
     * @param b second entity with a {@link Body}
     * @param anchor the weld point, world units
     * @return the joint
     */
    WeldJoint weld(Entity a, Entity b, Vec2 anchor);

    /**
     * Attaches wheel {@code b} to {@code a} on a suspension along an axis.
     *
     * @param a the chassis, with a {@link Body}
     * @param b the wheel, with a {@link Body}
     * @param anchor the wheel centre, world units
     * @param axis the suspension direction
     * @return the joint
     */
    WheelJoint wheel(Entity a, Entity b, Vec2 anchor, Vec2 axis);

    /**
     * Pulls a body towards a target point.
     *
     * @param body an entity with a dynamic {@link Body}
     * @param target the point grabbed, world units; it is pulled towards later targets
     * @return the joint
     */
    MouseJoint mouse(Entity body, Vec2 target);

    /**
     * Drives {@code b} towards its current offset and angle relative to {@code a}.
     *
     * @param a first entity with a {@link Body}
     * @param b second entity with a {@link Body}
     * @return the joint
     */
    MotorJoint motor(Entity a, Entity b);

    /**
     * Returns the joints of this world.
     *
     * @return the joints
     */
    List<Joint> joints();
}
