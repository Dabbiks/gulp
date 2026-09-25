package dev.gulp.api.ui;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.render.Draw;
import dev.gulp.api.text.Text;
import dev.gulp.api.text.TextBox;
import dev.gulp.api.text.TextLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Shows the chosen option; click or accept opens the list of options in a popup ({@link MenuPopup}), left and right
 * with the gamepad step through them. Created by {@link Ui#dropdown}. Theme type: {@code button} (the closed box);
 * the list uses {@code menu} and {@code menu_item}.
 *
 * <pre>{@code
 * enum Quality { LOW, MEDIUM, HIGH }
 * State<Quality> quality = State.of(Quality.HIGH);
 * dropdown(List.of(Quality.values()), q -> tr("quality." + q.name().toLowerCase())).bind(quality);
 * }</pre>
 *
 * @param <T> the option type
 */
public final class Dropdown<T> extends Node<Dropdown<T>> {

    private final List<T> options;
    private final Function<? super T, String> labels;
    private int selected;
    private @Nullable TextLayout layout;
    private @Nullable Style layoutStyle;
    private int layoutFor = -1;

    /**
     * Creates a dropdown with the first option chosen.
     *
     * @param options the options
     * @param labels the text of each option
     * @throws IllegalArgumentException if there are no options
     */
    public Dropdown(List<? extends T> options, Function<? super T, String> labels) {
        if (options.isEmpty()) {
            throw new IllegalArgumentException("A dropdown needs at least one option");
        }
        this.options = new ArrayList<>(options);
        this.labels = labels;
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

    /**
     * Chooses an option without firing a change.
     *
     * @param value the option; unknown values are ignored
     * @return this dropdown
     */
    public Dropdown<T> value(T value) {
        int index = options.indexOf(value);
        if (index >= 0 && index != selected) {
            selected = index;
            invalidate();
        }
        return this;
    }

    /**
     * Returns the chosen option.
     *
     * @return the option
     */
    public T value() {
        return options.get(selected);
    }

    /**
     * Keeps the choice in a state, both ways.
     *
     * @param state the choice
     * @return this dropdown
     */
    public Dropdown<T> bind(State<T> state) {
        bind(state, this::value);
        onChange(state::set);
        return this;
    }

    /**
     * Runs an action when the user chooses an option.
     *
     * @param action receives the option
     * @return this dropdown
     */
    @SuppressWarnings("unchecked")
    public Dropdown<T> onChange(Consumer<T> action) {
        return on(NodeEvent.Change.class, e -> action.accept((T) e.value()));
    }

    private void choose(int index) {
        if (index != selected && index >= 0 && index < options.size()) {
            selected = index;
            invalidate();
            fireChange(options.get(index));
        }
    }

    @Override
    protected void click() {
        super.click();
        List<Menu.Item> items = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            int index = i;
            items.add(new Menu.Item(labels.apply(options.get(i)), () -> choose(index), true));
        }
        new MenuPopup(new Menu(items)).open(this, x, y + height + 2f, width, selected);
    }

    @Override
    protected boolean navigate(UiAction action) {
        if (action == UiAction.LEFT) {
            choose(Math.max(0, selected - 1));
            return true;
        }
        if (action == UiAction.RIGHT) {
            choose(Math.min(options.size() - 1, selected + 1));
            return true;
        }
        return super.navigate(action);
    }

    private TextLayout layout() {
        Style style = style();
        TextLayout current = layout;
        if (current == null || layoutStyle != style || layoutFor != selected) {
            current = layoutText(Text.of(labels.apply(options.get(selected))), style.textStyle(), TextBox.NONE);
            layout = current;
            layoutStyle = style;
            layoutFor = selected;
        }
        return current;
    }

    @Override
    protected Size measure() {
        float widest = 0f;
        Style style = style();
        for (T option : options) {
            widest = Math.max(
                    widest,
                    layoutText(Text.of(labels.apply(option)), style.textStyle(), TextBox.NONE)
                            .width());
        }
        return new Size(widest + 24f, layout().height());
    }

    @Override
    protected void draw(Draw draw) {
        drawBackground(draw);
        Style style = style();
        Insets pad = style.padding();
        TextLayout current = layout();
        draw.text(current, x + pad.left(), y + (height - current.height()) / 2f);
        float ax = x + width - pad.right() - 6f;
        float ay = y + height / 2f;
        Color previous = draw.color();
        draw.color(style.textColor()).triangle(ax - 5f, ay - 2f, ax + 5f, ay - 2f, ax, ay + 4f);
        draw.color(previous);
    }
}
