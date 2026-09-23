package dev.gulp.api.registry;

/**
 * Something identified by a {@link Key}; everything stored in a {@link Registry} is keyed.
 *
 * <pre>{@code
 * record Item(Key key, int price) implements Keyed {}
 * }</pre>
 */
public interface Keyed {

    /**
     * Returns the key of this object.
     *
     * @return the key
     */
    Key key();
}
