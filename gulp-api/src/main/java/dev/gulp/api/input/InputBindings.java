package dev.gulp.api.input;

import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The player's current bindings. Changes are saved in {@code preferences()} automatically and survive restarts.
 *
 * <pre>{@code
 * // a "press a key" button in the settings screen
 * input().bindings().captureNextInput(binding -> {
 *     List<InputAction> clashes = input().bindings().conflicts(JUMP, binding);
 *     input().bindings().rebind(JUMP, 0, binding);
 * });
 * }</pre>
 */
public interface InputBindings {

    /**
     * Returns the bindings of an action, one per slot; unbound slots are {@code null}.
     *
     * @param action the action
     * @return the bindings, a snapshot
     */
    List<@Nullable Binding> of(InputAction action);

    /**
     * Puts a binding in a slot. A slot one past the last adds a slot.
     *
     * @param action the action
     * @param slot the slot, from 0
     * @param binding the new binding
     * @throws IllegalArgumentException if the slot is negative or more than one past the last
     */
    void rebind(InputAction action, int slot, Binding binding);

    /**
     * Empties a slot.
     *
     * @param action the action
     * @param slot the slot
     */
    void unbind(InputAction action, int slot);

    /**
     * Restores the default bindings of an action.
     *
     * @param action the action
     */
    void reset(InputAction action);

    /** Restores the default bindings of every action. */
    void resetAll();

    /**
     * Returns other actions in the same set that use a binding, so a settings screen can warn or swap.
     *
     * @param action the action being changed
     * @param binding the binding it would get
     * @return the conflicting actions, empty if none
     */
    List<InputAction> conflicts(InputAction action, Binding binding);

    /**
     * Waits for the next key, mouse button, gamepad button or stick push and hands it to the callback instead of to
     * actions. The captured input does not trigger actions until it is released.
     *
     * @param callback receives the binding on the main thread
     */
    void captureNextInput(Consumer<Binding> callback);

    /** Stops waiting without calling the callback. */
    void cancelCapture();

    /**
     * Returns whether {@link #captureNextInput(Consumer)} is waiting.
     *
     * @return {@code true} while capturing
     */
    boolean isCapturing();
}
