/**
 * Input: device-independent actions with rebindable bindings, and direct access to the keyboard, mouse, touch,
 * gamepads, cursor and clipboard.
 *
 * <pre>{@code
 * JUMP = registries().register(Registries.INPUT_ACTION, InputAction.builder(key("jump"))
 *         .bind(Keys.SPACE, GamepadButton.SOUTH).build());
 * if (input().justPressed(JUMP)) { jump(); }
 * }</pre>
 */
@NullMarked
package dev.gulp.api.input;

import org.jspecify.annotations.NullMarked;
