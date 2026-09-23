package dev.gulp.api.data;

import org.jspecify.annotations.Nullable;

/**
 * Immutable JSON value: the common data model of codecs, configs and saved data.
 *
 * <pre>{@code
 * JsonValue value = Json.parse("{\"hp\": 20, \"name\": \"Slime\"}");
 * int hp = value.asObject().get("hp").asInt();
 *
 * JsonObject built = JsonObject.builder().put("hp", 20).put("name", "Slime").build();
 * }</pre>
 */
public sealed interface JsonValue permits JsonNull, JsonBoolean, JsonNumber, JsonString, JsonArray, JsonObject {

    /**
     * Returns whether this is {@code null}.
     *
     * @return {@code true} for {@link JsonNull}
     */
    default boolean isNull() {
        return this instanceof JsonNull;
    }

    /**
     * Returns the boolean value.
     *
     * @return the value
     * @throws IllegalStateException if this is not a boolean
     */
    default boolean asBoolean() {
        if (this instanceof JsonBoolean b) {
            return b.value();
        }
        throw wrongType("boolean");
    }

    /**
     * Returns the number as {@code int}.
     *
     * @return the value, truncated towards zero if fractional
     * @throws IllegalStateException if this is not a number
     */
    default int asInt() {
        return asNumber().intValue();
    }

    /**
     * Returns the number as {@code long}.
     *
     * @return the value, truncated towards zero if fractional
     * @throws IllegalStateException if this is not a number
     */
    default long asLong() {
        return asNumber().longValue();
    }

    /**
     * Returns the number as {@code float}.
     *
     * @return the value
     * @throws IllegalStateException if this is not a number
     */
    default float asFloat() {
        return (float) asNumber().doubleValue();
    }

    /**
     * Returns the number as {@code double}.
     *
     * @return the value
     * @throws IllegalStateException if this is not a number
     */
    default double asDouble() {
        return asNumber().doubleValue();
    }

    /**
     * Returns this value as a number.
     *
     * @return the number
     * @throws IllegalStateException if this is not a number
     */
    default JsonNumber asNumber() {
        if (this instanceof JsonNumber n) {
            return n;
        }
        throw wrongType("number");
    }

    /**
     * Returns the string value.
     *
     * @return the value
     * @throws IllegalStateException if this is not a string
     */
    default String asString() {
        if (this instanceof JsonString s) {
            return s.value();
        }
        throw wrongType("string");
    }

    /**
     * Returns this value as an array.
     *
     * @return the array
     * @throws IllegalStateException if this is not an array
     */
    default JsonArray asArray() {
        if (this instanceof JsonArray a) {
            return a;
        }
        throw wrongType("array");
    }

    /**
     * Returns this value as an object.
     *
     * @return the object
     * @throws IllegalStateException if this is not an object
     */
    default JsonObject asObject() {
        if (this instanceof JsonObject o) {
            return o;
        }
        throw wrongType("object");
    }

    /**
     * Converts a plain Java value to JSON: {@code null}, {@link Boolean}, {@link Number}, {@link CharSequence},
     * {@link JsonValue}, {@link Iterable} and {@link java.util.Map} with string keys (recursively).
     *
     * @param value the value
     * @return the JSON value
     * @throws IllegalArgumentException for other types
     */
    static JsonValue of(@Nullable Object value) {
        return switch (value) {
            case null -> JsonNull.INSTANCE;
            case JsonValue json -> json;
            case Boolean b -> JsonBoolean.of(b);
            case Integer i -> JsonNumber.of(i);
            case Long l -> JsonNumber.of(l);
            case Short s -> JsonNumber.of(s);
            case Byte b -> JsonNumber.of(b);
            case Number n -> JsonNumber.of(n.doubleValue());
            case CharSequence s -> new JsonString(s.toString());
            case Iterable<?> items -> {
                JsonArray.Builder builder = JsonArray.builder();
                for (Object item : items) {
                    builder.add(of(item));
                }
                yield builder.build();
            }
            case java.util.Map<?, ?> map -> {
                JsonObject.Builder builder = JsonObject.builder();
                for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                    builder.put(String.valueOf(entry.getKey()), of(entry.getValue()));
                }
                yield builder.build();
            }
            default ->
                throw new IllegalArgumentException(
                        "Cannot convert " + value.getClass().getName() + " to JSON");
        };
    }

    private IllegalStateException wrongType(String expected) {
        String actual =
                switch (this) {
                    case JsonNull n -> "null";
                    case JsonBoolean b -> "boolean";
                    case JsonNumber n -> "number";
                    case JsonString s -> "string";
                    case JsonArray a -> "array";
                    case JsonObject o -> "object";
                };
        return new IllegalStateException("Expected a JSON " + expected + " but found " + actual);
    }
}
