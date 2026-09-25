package dev.gulp.api.ui;

import dev.gulp.api.input.Binding;
import dev.gulp.api.input.InputAction;
import dev.gulp.api.input.InputBindings;
import dev.gulp.api.render.Draw;
import dev.gulp.api.spi.UiAccess;
import dev.gulp.api.text.Text;
import dev.gulp.api.text.TextBox;
import dev.gulp.api.text.TextLayout;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Shows the binding in one slot of an action and, when pressed, waits for the next key, button or stick and binds it
 * there (saved in preferences). Created by {@link Ui#keybindButton}. Theme type: {@code button}.
 *
 * <pre>{@code
 * grid(3,
 *         label("Jump"), keybindButton(JUMP, 0), keybindButton(JUMP, 1),
 *         label("Fire"), keybindButton(FIRE, 0), keybindButton(FIRE, 1));
 * }</pre>
 */
public final class KeybindButton extends Node<KeybindButton> {

    private final InputAction action;
    private final int slot;
    private boolean waiting;
    private String shown = "";
    private @Nullable TextLayout layout;
    private @Nullable Style layoutStyle;
    private String layoutText = "";

    /**
     * Creates the button.
     *
     * @param action the action
     * @param slot the binding slot, from 0
     */
    public KeybindButton(InputAction action, int slot) {
        this.action = action;
        this.slot = slot;
    }

    @Override
    protected String styleType() {
        return "button";
    }

    @Override
    protected boolean isFocusableByDefault() {
        return true;
    }

    @Override
    protected MouseFilter defaultMouseFilter() {
        return MouseFilter.STOP;
    }

    @Override
    protected boolean isClickable() {
        return true;
    }

    @Override
    protected boolean isChecked() {
        return waiting;
    }

    /**
     * Returns whether the button waits for input.
     *
     * @return {@code true} while capturing
     */
    public boolean isWaiting() {
        return waiting;
    }

    private String current() {
        if (waiting) {
            return "...";
        }
        if (!UiAccess.hasBackend()) {
            return "-";
        }
        List<@Nullable Binding> bindings = UiAccess.backend().input().bindings().of(action);
        Binding binding = slot < bindings.size() ? bindings.get(slot) : null;
        return binding == null ? "-" : binding.displayName();
    }

    @Override
    protected void click() {
        super.click();
        InputBindings bindings = UiAccess.backend().input().bindings();
        waiting = true;
        refreshState();
        invalidate();
        bindings.captureNextInput(binding -> {
            waiting = false;
            bindings.rebind(action, slot, binding);
            refreshState();
            invalidate();
            fireChange(binding);
        });
    }

    @Override
    protected void unmounted() {
        if (waiting && UiAccess.hasBackend()) {
            UiAccess.backend().input().bindings().cancelCapture();
            waiting = false;
        }
    }

    private TextLayout layout() {
        Style style = style();
        String text = current();
        TextLayout existing = layout;
        if (existing == null || layoutStyle != style || !layoutText.equals(text)) {
            existing = layoutText(Text.of(text), style.textStyle(), TextBox.NONE);
            layout = existing;
            layoutStyle = style;
            layoutText = text;
        }
        return existing;
    }

    @Override
    protected Size measure() {
        TextLayout current = layout();
        return new Size(Math.max(current.width(), 72f), current.height());
    }

    @Override
    protected void update(float seconds) {
        String text = current();
        if (!text.equals(shown)) {
            shown = text;
            invalidate();
        }
    }

    @Override
    protected void draw(Draw draw) {
        drawBackground(draw);
        TextLayout current = layout();
        draw.text(current, x + (width - current.width()) / 2f, y + (height - current.height()) / 2f);
    }
}
