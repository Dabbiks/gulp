package dev.gulp.api.registry;

/**
 * Identifier {@code namespace:path} of registered content, assets and data.
 *
 * <pre>{@code
 * Key coin = Key.of("coins", "coin");
 * Key sprite = Key.parse("coins:sprites/player");
 * Key local = key("coin");              // inside a Game or GameModule: the game namespace
 * }</pre>
 *
 * <p>The namespace matches {@code [a-z0-9_.-]+}, the path {@code [a-z0-9_./-]+}. The namespace {@value #RESERVED} is
 * reserved for the engine.
 *
 * @param namespace the namespace, usually the game id
 * @param path the path within the namespace
 */
public record Key(String namespace, String path) implements Comparable<Key> {

    /** Namespace reserved for built-in engine content. */
    public static final String RESERVED = "gulp";

    /**
     * Validates both parts.
     *
     * @throws IllegalArgumentException if a part is empty or contains a character outside its allowed set
     */
    public Key {
        requireValid("namespace", namespace, false);
        requireValid("path", path, true);
    }

    /**
     * Creates a key.
     *
     * @param namespace the namespace
     * @param path the path
     * @return the key
     * @throws IllegalArgumentException if a part is invalid
     */
    public static Key of(String namespace, String path) {
        return new Key(namespace, path);
    }

    /**
     * Parses {@code namespace:path}.
     *
     * @param text the text
     * @return the key
     * @throws IllegalArgumentException if there is no single {@code :} or a part is invalid
     */
    public static Key parse(String text) {
        int colon = text.indexOf(':');
        if (colon < 0 || text.indexOf(':', colon + 1) >= 0) {
            throw new IllegalArgumentException("Key must have the form namespace:path, got '" + text + "'");
        }
        return new Key(text.substring(0, colon), text.substring(colon + 1));
    }

    /**
     * Parses {@code namespace:path}, or {@code path} alone in a default namespace.
     *
     * @param text the text
     * @param defaultNamespace namespace used when the text has no {@code :}
     * @return the key
     * @throws IllegalArgumentException if a part is invalid
     */
    public static Key parse(String text, String defaultNamespace) {
        return text.indexOf(':') < 0 ? new Key(defaultNamespace, text) : parse(text);
    }

    /**
     * Returns whether a string is a valid namespace.
     *
     * @param namespace the candidate
     * @return {@code true} if it matches {@code [a-z0-9_.-]+}
     */
    public static boolean isValidNamespace(String namespace) {
        return isValid(namespace, false);
    }

    /**
     * Returns whether this key is in the reserved engine namespace.
     *
     * @return {@code true} for {@code gulp:*}
     */
    public boolean isReserved() {
        return RESERVED.equals(namespace);
    }

    /**
     * Returns {@code namespace:path}.
     *
     * @return the text form
     */
    @Override
    public String toString() {
        return namespace + ':' + path;
    }

    @Override
    public int compareTo(Key other) {
        int byNamespace = namespace.compareTo(other.namespace);
        return byNamespace != 0 ? byNamespace : path.compareTo(other.path);
    }

    private static void requireValid(String part, String value, boolean allowSlash) {
        if (!isValid(value, allowSlash)) {
            throw new IllegalArgumentException("Invalid key " + part + " '" + value + "': allowed characters are "
                    + (allowSlash ? "[a-z0-9_./-]" : "[a-z0-9_.-]"));
        }
    }

    private static boolean isValid(String value, boolean allowSlash) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '_'
                    || c == '.'
                    || c == '-'
                    || (allowSlash && c == '/');
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
