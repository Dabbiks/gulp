package dev.gulp.api.data;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An immutable JSON array.
 *
 * <pre>{@code
 * JsonArray scores = JsonArray.builder().add(10).add(25).build();
 * for (JsonValue score : scores) total += score.asInt();
 * }</pre>
 *
 * @param values the elements, copied into an unmodifiable list
 */
public record JsonArray(List<JsonValue> values) implements JsonValue, Iterable<JsonValue> {

    /** The empty array. */
    public static final JsonArray EMPTY = new JsonArray(List.of());

    /** Copies the elements. */
    public JsonArray {
        values = List.copyOf(values);
    }

    /**
     * Starts building an array.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the number of elements.
     *
     * @return the size
     */
    public int size() {
        return values.size();
    }

    /**
     * Returns an element.
     *
     * @param index the index
     * @return the element
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public JsonValue get(int index) {
        return values.get(index);
    }

    @Override
    public Iterator<JsonValue> iterator() {
        return values.iterator();
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
     * Builds a {@link JsonArray}.
     *
     * <pre>{@code
     * JsonArray.builder().add("a").add(1).add(true).build();
     * }</pre>
     */
    public static final class Builder {

        private final List<JsonValue> values = new ArrayList<>();

        private Builder() {}

        /**
         * Appends a value.
         *
         * @param value the value
         * @return this builder
         */
        public Builder add(JsonValue value) {
            values.add(value);
            return this;
        }

        /**
         * Appends a string.
         *
         * @param value the string
         * @return this builder
         */
        public Builder add(String value) {
            return add(new JsonString(value));
        }

        /**
         * Appends a number.
         *
         * @param value the number
         * @return this builder
         */
        public Builder add(long value) {
            return add(JsonNumber.of(value));
        }

        /**
         * Appends a number.
         *
         * @param value the number
         * @return this builder
         */
        public Builder add(double value) {
            return add(JsonNumber.of(value));
        }

        /**
         * Appends a boolean.
         *
         * @param value the boolean
         * @return this builder
         */
        public Builder add(boolean value) {
            return add(JsonBoolean.of(value));
        }

        /**
         * Builds the array.
         *
         * @return the immutable array
         */
        public JsonArray build() {
            return new JsonArray(values);
        }
    }
}
