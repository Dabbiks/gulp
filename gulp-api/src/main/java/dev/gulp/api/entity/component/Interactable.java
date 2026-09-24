package dev.gulp.api.entity.component;

import dev.gulp.api.entity.Component;
import dev.gulp.api.input.Cursor;
import dev.gulp.api.math.Rect;
import org.jspecify.annotations.Nullable;

/**
 * Makes the entity react to the pointer: {@code EntityClickEvent}, {@code EntityHoverEnterEvent} and {@code
 * EntityHoverExitEvent}. The pointer is tested against the entity bounds (or a custom area) through the camera under
 * it; when entities overlap, the one drawn on top wins. Input the UI consumed does not reach entities.
 *
 * <pre>{@code
 * chest.add(new Interactable().cursor(SystemCursor.HAND));
 * chest.on(EntityClickEvent.class, e -> open(chest));
 * }</pre>
 */
public final class Interactable extends Component {

    private @Nullable Rect area;
    private @Nullable Cursor cursor;
    private boolean hovered;

    /** Creates the component. */
    public Interactable() {}

    /**
     * Returns the area tested against the pointer.
     *
     * @return relative to the entity position in world units, or {@code null} for the bounds
     */
    public @Nullable Rect area() {
        return area;
    }

    /**
     * Sets the area tested against the pointer.
     *
     * @param value relative to the entity position, or {@code null} for the bounds
     * @return this component
     */
    public Interactable area(@Nullable Rect value) {
        this.area = value;
        return this;
    }

    /**
     * Returns the cursor shown while hovered.
     *
     * @return the cursor, or {@code null} to leave it unchanged
     */
    public @Nullable Cursor cursor() {
        return cursor;
    }

    /**
     * Shows a cursor while hovered.
     *
     * @param value the cursor
     * @return this component
     */
    public Interactable cursor(@Nullable Cursor value) {
        this.cursor = value;
        return this;
    }

    /**
     * Returns whether the pointer is over the entity.
     *
     * @return {@code true} while hovered
     */
    public boolean isHovered() {
        return hovered;
    }

    /**
     * Records the hover state; called by the engine.
     *
     * @param value whether hovered
     */
    public void markHovered(boolean value) {
        this.hovered = value;
    }
}
