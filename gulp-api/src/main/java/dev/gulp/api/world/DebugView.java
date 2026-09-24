package dev.gulp.api.world;

/**
 * Debug drawings of a world, drawn over it in the world's cameras.
 *
 * <pre>{@code
 * world.showDebug(DebugView.SHAPES, true);
 * world.showDebug(DebugView.PATHS, true);
 * }</pre>
 */
public enum DebugView {
    /** Collision shapes of tiles, colliders, movers and bodies; sleeping bodies are grey. */
    SHAPES,
    /** Rigid body contact points and normals. */
    CONTACTS,
    /** Joints and their anchors. */
    JOINTS,
    /** Trigger areas. */
    TRIGGERS,
    /** Blocked and costly cells of the navigation grid around the camera. */
    NAVIGATION,
    /** Paths of navigation agents and path followers. */
    PATHS,
    /** Velocities and steering forces of steering agents. */
    STEERING
}
