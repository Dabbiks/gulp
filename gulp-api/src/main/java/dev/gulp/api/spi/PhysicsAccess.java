package dev.gulp.api.spi;

import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.physics.Body;
import dev.gulp.api.physics.CollisionLayer;
import dev.gulp.api.physics.Contact;
import dev.gulp.api.physics.Mover;
import dev.gulp.api.physics.Trigger;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Connects the physics components of the API with the physics of {@code gulp-core}. For {@code gulp-core} only.
 *
 * <pre>{@code
 * PhysicsAccess.installBackend(new PhysicsBackend(worlds));
 * PhysicsAccess.assignBit(CollisionLayer.DEFAULT, 0);
 * }</pre>
 */
public final class PhysicsAccess {

    /** Implemented by the engine. */
    public interface Backend {
        /**
         * Starts simulating a collider, mover or trigger whose entity entered its world.
         *
         * @param component the component
         */
        void attach(Component component);

        /**
         * Stops simulating a component.
         *
         * @param component the component
         */
        void detach(Component component);

        /**
         * Starts simulating a body.
         *
         * @param body the body, spawned
         * @return the live state of the body
         */
        BodyHandle attachBody(Body body);

        /**
         * Moves a mover and reports the outcome.
         *
         * @param mover the mover
         * @param velocityX x velocity in units per second, gravity already added
         * @param velocityY y velocity
         * @param out receives the new velocity, flags and contacts
         */
        void moveAndSlide(Mover mover, float velocityX, float velocityY, MoveResult out);

        /**
         * Returns the entities inside a trigger.
         *
         * @param trigger the trigger
         * @return the entities, empty when not simulated
         */
        List<Entity> triggered(Trigger trigger);
    }

    /** The live state of a simulated body. */
    public interface BodyHandle {
        /**
         * Returns the x velocity.
         *
         * @return units per second
         */
        float velocityX();

        /**
         * Returns the y velocity.
         *
         * @return units per second
         */
        float velocityY();

        /**
         * Returns the angular velocity.
         *
         * @return degrees per second
         */
        float angularVelocity();

        /**
         * Sets the velocity and wakes the body.
         *
         * @param x units per second
         * @param y units per second
         */
        void setVelocity(float x, float y);

        /**
         * Sets the angular velocity and wakes the body.
         *
         * @param degreesPerSecond the velocity
         */
        void setAngularVelocity(float degreesPerSecond);

        /**
         * Adds a force for the next step, at a world point.
         *
         * @param fx force x
         * @param fy force y
         * @param px point x
         * @param py point y
         */
        void applyForce(float fx, float fy, float px, float py);

        /**
         * Changes the velocity at once by an impulse at a world point.
         *
         * @param ix impulse x
         * @param iy impulse y
         * @param px point x
         * @param py point y
         */
        void applyImpulse(float ix, float iy, float px, float py);

        /**
         * Adds a torque for the next step.
         *
         * @param torque the torque
         */
        void applyTorque(float torque);

        /**
         * Returns whether the body is awake.
         *
         * @return {@code true} if simulated this tick
         */
        boolean isAwake();

        /**
         * Wakes the body or puts it to sleep.
         *
         * @param awake whether awake
         */
        void setAwake(boolean awake);

        /**
         * Returns the mass.
         *
         * @return the mass; {@code 0} for static and kinematic bodies
         */
        float mass();

        /** Re-reads the settings of the body, such as its type and shape. */
        void refresh();
    }

    /** Outcome of {@link Backend#moveAndSlide}; reused by each mover. */
    public static final class MoveResult {
        /** New x velocity. */
        public float velocityX;

        /** New y velocity. */
        public float velocityY;

        /** Whether the mover stands on a floor. */
        public boolean floor;

        /** Whether the mover touches a wall. */
        public boolean wall;

        /** Whether the mover touches a ceiling. */
        public boolean ceiling;

        /** Floor normal x. */
        public float floorNormalX;

        /** Floor normal y. */
        public float floorNormalY = -1f;

        /** Wall normal x. */
        public float wallNormalX;

        /** Wall normal y. */
        public float wallNormalY;

        /** Entity under the mover, or {@code null} on tiles or in the air. */
        public @Nullable Entity floorEntity;

        /** Contacts of the move. */
        public final List<Contact> contacts = new ArrayList<>();

        /** Creates an empty result. */
        public MoveResult() {}
    }

    /** Implemented inside {@link CollisionLayer}. */
    public interface LayerHooks {
        /**
         * Assigns the mask bit of a layer.
         *
         * @param layer the layer
         * @param bit the bit
         */
        void assignBit(CollisionLayer layer, int bit);
    }

    private static @Nullable Backend backend;
    private static @Nullable LayerHooks layers;

    private PhysicsAccess() {}

    /**
     * Installs the engine side.
     *
     * @param installed the backend
     */
    public static void installBackend(Backend installed) {
        backend = installed;
    }

    /**
     * Installs the layer hooks; called by {@link CollisionLayer}.
     *
     * @param installed the hooks
     */
    public static void installLayers(LayerHooks installed) {
        if (layers == null) {
            layers = installed;
        }
    }

    /**
     * Returns the engine side.
     *
     * @return the backend
     * @throws IllegalStateException without a running engine
     */
    public static Backend backend() {
        Backend current = backend;
        if (current == null) {
            throw new IllegalStateException("Physics runs only inside a started engine");
        }
        return current;
    }

    /**
     * Assigns the mask bit of a layer.
     *
     * @param layer the layer; creating it installed the hooks
     * @param bit the bit
     */
    public static void assignBit(CollisionLayer layer, int bit) {
        LayerHooks current = layers;
        if (current == null) {
            throw new IllegalStateException("CollisionLayer hooks are not installed");
        }
        current.assignBit(layer, bit);
    }
}
