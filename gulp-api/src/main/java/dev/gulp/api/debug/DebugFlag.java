package dev.gulp.api.debug;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Debug drawings and inspectors that F3 + a key or {@code /debug <name>} switches.
 *
 * <pre>{@code
 * debug().toggle(DebugFlag.NAVIGATION); // same as F3 + N
 * }</pre>
 */
public enum DebugFlag {
    /** Collision shapes, triggers, contacts and joints (F3 + C). */
    COLLISION('C'),
    /** The navigation grid, paths of agents and steering forces (F3 + N). */
    NAVIGATION('N'),
    /** Chunk borders with their coordinates (F3 + G). */
    CHUNKS('G'),
    /** Light radii and occluders (F3 + L). */
    LIGHTS('L'),
    /** The UI inspector: the node under the pointer with its size and style (F3 + U). */
    UI_INSPECTOR('U'),
    /** The entity inspector: the clicked entity with its components, tags and data, live (F3 + E). */
    ENTITY_INSPECTOR('E');

    private final char key;

    DebugFlag(char key) {
        this.key = key;
    }

    /**
     * Returns the letter pressed together with F3.
     *
     * @return an upper-case letter
     */
    public char key() {
        return key;
    }

    /**
     * Returns the name used by {@code /debug}.
     *
     * @return the lower-case name, such as {@code "ui_inspector"}
     */
    public String commandName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Finds a flag by its {@link #commandName()}, ignoring case.
     *
     * @param name the name
     * @return the flag, or {@code null}
     */
    public static @Nullable DebugFlag byName(String name) {
        for (DebugFlag flag : values()) {
            if (flag.commandName().equalsIgnoreCase(name)) {
                return flag;
            }
        }
        return null;
    }
}
