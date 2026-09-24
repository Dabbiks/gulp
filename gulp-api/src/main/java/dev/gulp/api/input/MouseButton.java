package dev.gulp.api.input;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Mouse buttons. The first finger on a touch screen acts as {@link #LEFT}.
 *
 * <pre>{@code
 * if (input().isDown(MouseButton.LEFT)) {
 *     paint(input().mouseX(), input().mouseY());
 * }
 * }</pre>
 */
public enum MouseButton implements Binding {
    /** The primary button. */
    LEFT("Left mouse"),
    /** The secondary button. */
    RIGHT("Right mouse"),
    /** The wheel button. */
    MIDDLE("Middle mouse"),
    /** The side back button. */
    BACK("Mouse back"),
    /** The side forward button. */
    FORWARD("Mouse forward");

    private final String displayName;

    MouseButton(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the button with a platform index.
     *
     * @param index 0 left, 1 right, 2 middle, 3 back, 4 forward
     * @return the button, or {@code null} for other indices
     */
    public static @Nullable MouseButton ofIndex(int index) {
        MouseButton[] values = values();
        return index >= 0 && index < values.length ? values[index] : null;
    }

    /**
     * Returns the platform index of this button.
     *
     * @return the index, 0 for {@link #LEFT}
     */
    public int index() {
        return ordinal();
    }

    @Override
    public String id() {
        return "mouse:" + name().toLowerCase(Locale.ROOT);
    }

    @Override
    public InputDevice device() {
        return InputDevice.MOUSE;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public BindingGlyph glyph(ControllerFamily family) {
        return new BindingGlyph(family, displayName, "input/mouse/" + name().toLowerCase(Locale.ROOT));
    }
}
