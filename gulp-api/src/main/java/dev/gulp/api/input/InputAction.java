package dev.gulp.api.input;

import dev.gulp.api.registry.Key;
import dev.gulp.api.registry.Keyed;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An input action: something the player does ("jump", "move left"), independent of the device. It has default
 * bindings, each in its own slot, which the player may change through {@link Input#bindings()}. Register actions in
 * {@code Registries.INPUT_ACTION} during {@code onLoad} so that settings screens can list them.
 *
 * <pre>{@code
 * JUMP = registries().register(Registries.INPUT_ACTION, InputAction.builder(key("jump"))
 *         .bind(Keys.SPACE, Keys.W, GamepadButton.SOUTH)
 *         .build());
 * MOVE_LEFT = registries().register(Registries.INPUT_ACTION, InputAction.builder(key("move_left"))
 *         .bind(Keys.A, Keys.LEFT, GamepadAxis.LEFT_X.negative(), GamepadButton.DPAD_LEFT)
 *         .build());
 * // in a tick:
 * if (input().justPressed(JUMP)) { jump(); }
 * }</pre>
 */
public final class InputAction implements Keyed {

    /** Default dead zone of analog bindings. */
    public static final float DEFAULT_DEAD_ZONE = 0.2f;

    private final Key key;
    private final List<Binding> defaults;
    private final ActionSet set;
    private final float deadZone;

    private InputAction(Builder builder) {
        this.key = builder.key;
        this.defaults = Collections.unmodifiableList(new ArrayList<>(builder.bindings));
        this.set = builder.set;
        this.deadZone = builder.deadZone;
    }

    /**
     * Starts building an action.
     *
     * @param key the action key, for example {@code key("jump")}
     * @return the builder
     */
    public static Builder builder(Key key) {
        return new Builder(key);
    }

    @Override
    public Key key() {
        return key;
    }

    /**
     * Returns the default bindings, one per slot.
     *
     * @return the bindings
     */
    public List<Binding> defaultBindings() {
        return defaults;
    }

    /**
     * Returns the set this action belongs to.
     *
     * @return the set, {@link ActionSet#GAMEPLAY} unless chosen otherwise
     */
    public ActionSet set() {
        return set;
    }

    /**
     * Returns the dead zone of analog bindings: axis values below it read as zero, and the rest is rescaled to {@code
     * 0..1}.
     *
     * @return the dead zone, {@code 0..1}
     */
    public float deadZone() {
        return deadZone;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof InputAction action && action.key.equals(key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return "InputAction[" + key + "]";
    }

    /**
     * Builds an {@link InputAction}.
     *
     * <pre>{@code
     * InputAction aim = InputAction.builder(key("aim")).bind(MouseButton.RIGHT, GamepadAxis.LEFT_TRIGGER.positive())
     *         .deadZone(0.1f).build();
     * }</pre>
     */
    public static final class Builder {
        private final Key key;
        private final List<Binding> bindings = new ArrayList<>();
        private ActionSet set = ActionSet.GAMEPLAY;
        private float deadZone = DEFAULT_DEAD_ZONE;

        private Builder(Key key) {
            this.key = key;
        }

        /**
         * Adds default bindings, each in the next slot.
         *
         * @param newBindings the bindings
         * @return this builder
         */
        public Builder bind(Binding... newBindings) {
            Collections.addAll(bindings, newBindings);
            return this;
        }

        /**
         * Puts the action in a set.
         *
         * @param newSet the set
         * @return this builder
         */
        public Builder set(ActionSet newSet) {
            this.set = newSet;
            return this;
        }

        /**
         * Sets the dead zone of analog bindings.
         *
         * @param zone the dead zone, {@code 0..1} exclusive of 1
         * @return this builder
         * @throws IllegalArgumentException if the zone is out of range
         */
        public Builder deadZone(float zone) {
            if (!(zone >= 0f && zone < 1f)) {
                throw new IllegalArgumentException("Dead zone must be in [0, 1), got " + zone);
            }
            this.deadZone = zone;
            return this;
        }

        /**
         * Builds the action.
         *
         * @return the action
         */
        public InputAction build() {
            return new InputAction(this);
        }
    }
}
