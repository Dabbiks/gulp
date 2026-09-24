package dev.gulp.api.input;

/**
 * A group of actions switched on and off together, for example gameplay controls while a menu is open.
 *
 * <pre>{@code
 * InputAction pause = InputAction.builder(key("pause")).bind(Keys.ESCAPE).set(ActionSet.MENU).build();
 * input().disable(ActionSet.GAMEPLAY); // gameplay actions read as released until enabled again
 * }</pre>
 *
 * @param name the set name, {@code [a-z0-9_.-]+}
 */
public record ActionSet(String name) {

    /** Default set of actions: movement, attacks and the like. */
    public static final ActionSet GAMEPLAY = new ActionSet("gameplay");

    /** Menu navigation; stays enabled while gameplay is blocked. */
    public static final ActionSet MENU = new ActionSet("menu");

    /**
     * Validates the name.
     *
     * @param name the name
     */
    public ActionSet {
        if (name.isEmpty() || !name.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid action set name '" + name + "': use [a-z0-9_.-]+");
        }
    }

    /**
     * Returns a set with a name.
     *
     * @param name the name
     * @return the set
     */
    public static ActionSet of(String name) {
        return new ActionSet(name);
    }
}
