package dev.gulp.api.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * An immutable JSON object that keeps member order.
 *
 * <pre>{@code
 * JsonObject slime = JsonObject.builder().put("name", "Slime").put("hp", 20).build();
 * int hp = slime.getOrThrow("hp").asInt();
 * JsonObject hurt = slime.with("hp", JsonNumber.of(15));
 * }</pre>
 *
 * @param members the members, copied into an unmodifiable insertion-ordered map
 */
public record JsonObject(Map<String, JsonValue> members) implements JsonValue {

    /** The empty object. */
    public static final JsonObject EMPTY = new JsonObject(Map.of());

    /** Copies the members, keeping their order. */
    public JsonObject {
        members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
    }

    /**
     * Starts building an object.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a member.
     *
     * @param name the member name
     * @return the value, or {@code null} if absent
     */
    public @Nullable JsonValue get(String name) {
        return members.get(name);
    }

    /**
     * Returns a member that must exist.
     *
     * @param name the member name
     * @return the value
     * @throws IllegalStateException if absent
     */
    public JsonValue getOrThrow(String name) {
        JsonValue value = members.get(name);
        if (value == null) {
            throw new IllegalStateException("Missing JSON member '" + name + "'");
        }
        return value;
    }

    /**
     * Returns whether a member exists.
     *
     * @param name the member name
     * @return {@code true} if present
     */
    public boolean has(String name) {
        return members.containsKey(name);
    }

    /**
     * Returns the member names in order.
     *
     * @return an unmodifiable set
     */
    public Set<String> names() {
        return members.keySet();
    }

    /**
     * Returns the number of members.
     *
     * @return the size
     */
    public int size() {
        return members.size();
    }

    /**
     * Returns a copy with one member added or replaced.
     *
     * @param name the member name
     * @param value the value
     * @return the new object
     */
    public JsonObject with(String name, JsonValue value) {
        Map<String, JsonValue> copy = new LinkedHashMap<>(members);
        copy.put(name, value);
        return new JsonObject(copy);
    }

    /**
     * Returns a copy without one member.
     *
     * @param name the member name
     * @return the new object
     */
    public JsonObject without(String name) {
        Map<String, JsonValue> copy = new LinkedHashMap<>(members);
        copy.remove(name);
        return new JsonObject(copy);
    }

    /**
     * Returns compact JSON text.
     *
     * @return the JSON text
     */
    @Override
    public String toString() {
        return Json.write(this);
    }

    /**
     * Builds a {@link JsonObject}; later puts replace earlier ones with the same name.
     *
     * <pre>{@code
     * JsonObject.builder().put("x", 1.5).put("y", 2).build();
     * }</pre>
     */
    public static final class Builder {

        private final Map<String, JsonValue> members = new LinkedHashMap<>();

        private Builder() {}

        /**
         * Adds a member.
         *
         * @param name the name
         * @param value the value
         * @return this builder
         */
        public Builder put(String name, JsonValue value) {
            members.put(name, value);
            return this;
        }

        /**
         * Adds a string member.
         *
         * @param name the name
         * @param value the string
         * @return this builder
         */
        public Builder put(String name, String value) {
            return put(name, new JsonString(value));
        }

        /**
         * Adds a number member.
         *
         * @param name the name
         * @param value the number
         * @return this builder
         */
        public Builder put(String name, long value) {
            return put(name, JsonNumber.of(value));
        }

        /**
         * Adds a number member.
         *
         * @param name the name
         * @param value the number
         * @return this builder
         */
        public Builder put(String name, double value) {
            return put(name, JsonNumber.of(value));
        }

        /**
         * Adds a boolean member.
         *
         * @param name the name
         * @param value the boolean
         * @return this builder
         */
        public Builder put(String name, boolean value) {
            return put(name, JsonBoolean.of(value));
        }

        /**
         * Builds the object.
         *
         * @return the immutable object
         */
        public JsonObject build() {
            return new JsonObject(members);
        }
    }
}
