package dev.gulp.api.ui;

/**
 * The shared choice of a set of {@link Radio} options: exactly one value is selected.
 *
 * <pre>{@code
 * ButtonGroup<String> quality = new ButtonGroup<>("high");
 * row(radio("Low", quality, "low"), radio("High", quality, "high"));
 * String chosen = quality.value().get();
 * }</pre>
 *
 * @param <T> the value type
 */
public final class ButtonGroup<T> {

    private final State<T> value;

    /**
     * Creates a group.
     *
     * @param initial the selected value
     */
    public ButtonGroup(T initial) {
        this.value = State.of(initial);
    }

    /**
     * Returns the selected value as a state, to read, set or subscribe to.
     *
     * @return the state
     */
    public State<T> value() {
        return value;
    }
}
