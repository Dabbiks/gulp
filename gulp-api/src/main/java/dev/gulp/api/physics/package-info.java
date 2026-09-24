/**
 * Physics on two levels: kinematic ({@link dev.gulp.api.physics.Mover}, {@link dev.gulp.api.physics.Trigger}, {@link
 * dev.gulp.api.physics.Collider}) and rigid bodies ({@link dev.gulp.api.physics.Body} with joints), both in pure Java.
 * Layers and masks filter contacts; {@link dev.gulp.api.physics.Physics} answers ray, shape and overlap queries.
 *
 * <pre>{@code
 * PICKUP = registries().register(Registries.COLLISION_LAYER, CollisionLayer.of(key("pickup")));
 * player.add(new Mover().coyoteTicks(6));
 * coin.add(new Trigger().layer(PICKUP));
 * RayHit hit = world.physics().raycast(from, to, CollisionMask.ALL);
 * }</pre>
 */
@NullMarked
package dev.gulp.api.physics;

import org.jspecify.annotations.NullMarked;
