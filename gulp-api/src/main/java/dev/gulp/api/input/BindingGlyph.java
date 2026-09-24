package dev.gulp.api.input;

/**
 * How to show a binding to the player: a short label and the path of an icon. The engine does not ship icon images;
 * UI themes look the path up in their own atlas and fall back to drawing the label on a key cap.
 *
 * <pre>{@code
 * BindingGlyph glyph = GamepadButton.SOUTH.glyph(ControllerFamily.PLAYSTATION);
 * glyph.label(); // "Cross"
 * glyph.icon();  // "input/playstation/south"
 * }</pre>
 *
 * @param family the controller family the glyph is for
 * @param label short text, for example {@code A}, {@code Cross} or {@code Space}
 * @param icon icon path inside a theme namespace, for example {@code input/xbox/south}
 */
public record BindingGlyph(ControllerFamily family, String label, String icon) {}
