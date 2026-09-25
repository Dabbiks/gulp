package dev.gulp.api.ui;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.input.InputAction;
import dev.gulp.api.input.MouseButton;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.render.Draw;
import dev.gulp.api.spi.UiAccess;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * An on-screen stick for touch screens: drag the knob, release and it returns to the centre. It can drive four input
 * actions, so the game reads it like a real stick through {@code input().vector(...)}. Created by {@link
 * Ui#virtualJoystick}. Theme type: {@code joystick} (track for the base, accent for the knob).
 *
 * <pre>{@code
 * ui().hud().add(this, virtualJoystick().actions(LEFT, RIGHT, UP, DOWN).anchor(Anchor.BOTTOM_LEFT).offset(24, 24));
 * }</pre>
 */
public final class VirtualJoystick extends Node<VirtualJoystick> {

    private float valueX;
    private float valueY;
    private boolean dragging;
    private @Nullable InputAction left;
    private @Nullable InputAction right;
    private @Nullable InputAction up;
    private @Nullable InputAction down;

    /** Creates a joystick. */
    public VirtualJoystick() {}

    @Override
    protected String styleType() {
        return "joystick";
    }

    @Override
    protected MouseFilter defaultMouseFilter() {
        return MouseFilter.STOP;
    }

    @Override
    protected boolean usesPadding() {
        return false;
    }

    /**
     * Drives four actions with the stick's direction.
     *
     * @param leftAction held when pushed left
     * @param rightAction held when pushed right
     * @param upAction held when pushed up
     * @param downAction held when pushed down
     * @return this joystick
     */
    public VirtualJoystick actions(
            InputAction leftAction, InputAction rightAction, InputAction upAction, InputAction downAction) {
        this.left = leftAction;
        this.right = rightAction;
        this.up = upAction;
        this.down = downAction;
        return this;
    }

    /**
     * Returns the stick position.
     *
     * @return a vector of length at most 1, Y down
     */
    public Vec2 value() {
        return new Vec2(valueX, valueY);
    }

    /**
     * Runs an action when the stick moves.
     *
     * @param action receives the position
     * @return this joystick
     */
    public VirtualJoystick onChange(Consumer<Vec2> action) {
        return on(NodeEvent.Change.class, e -> action.accept((Vec2) e.value()));
    }

    private void set(float nx, float ny) {
        float length = (float) Math.sqrt(nx * nx + ny * ny);
        if (length > 1f) {
            nx /= length;
            ny /= length;
        }
        if (nx == valueX && ny == valueY) {
            return;
        }
        valueX = nx;
        valueY = ny;
        if (UiAccess.hasBackend()) {
            var input = UiAccess.backend().input();
            if (left != null && right != null && up != null && down != null) {
                input.setVirtualStrength(left, Math.max(0f, -nx));
                input.setVirtualStrength(right, Math.max(0f, nx));
                input.setVirtualStrength(up, Math.max(0f, -ny));
                input.setVirtualStrength(down, Math.max(0f, ny));
            }
        }
        fireChange(value());
    }

    @Override
    protected boolean pointerDown(float px, float py, MouseButton button) {
        dragging = true;
        pointerDrag(px, py);
        return true;
    }

    @Override
    protected void pointerDrag(float px, float py) {
        if (dragging) {
            float radius = Math.min(width, height) / 2f;
            set((px - x - width / 2f) / radius, (py - y - height / 2f) / radius);
        }
    }

    @Override
    protected void pointerUp(float px, float py, boolean inside) {
        dragging = false;
        set(0f, 0f);
    }

    @Override
    protected void unmounted() {
        dragging = false;
        set(0f, 0f);
    }

    @Override
    protected Size measure() {
        return new Size(96f, 96f);
    }

    @Override
    protected void draw(Draw draw) {
        Style style = style();
        float radius = Math.min(width, height) / 2f;
        float cx = x + width / 2f;
        float cy = y + height / 2f;
        Color previous = draw.color();
        draw.color(style.track()).circle(cx, cy, radius);
        draw.color(style.accent().withAlpha(dragging ? 0.95f : 0.7f));
        draw.circle(cx + valueX * radius * 0.6f, cy + valueY * radius * 0.6f, radius * 0.4f);
        draw.color(previous);
    }
}
