package dev.gulp.api.data;

/**
 * A JSON number. Integral numbers keep full {@code long} precision; others are stored as {@code double}.
 *
 * <pre>{@code
 * JsonNumber score = JsonNumber.of(9_007_199_254_740_993L);   // exact
 * JsonNumber speed = JsonNumber.of(7.5);
 * }</pre>
 */
public final class JsonNumber implements JsonValue {

    private final long longValue;
    private final double doubleValue;
    private final boolean integral;

    private JsonNumber(long longValue, double doubleValue, boolean integral) {
        this.longValue = longValue;
        this.doubleValue = doubleValue;
        this.integral = integral;
    }

    /**
     * Creates an integral number.
     *
     * @param value the value
     * @return the number
     */
    public static JsonNumber of(long value) {
        return new JsonNumber(value, value, true);
    }

    /**
     * Creates a number; whole values within {@code long} range become integral.
     *
     * @param value the value, finite
     * @return the number
     * @throws IllegalArgumentException if the value is NaN or infinite, which JSON cannot represent
     */
    public static JsonNumber of(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("JSON numbers must be finite, got " + value);
        }
        long asLong = (long) value;
        if (asLong == value && Math.abs(value) < 9.0e15) {
            return new JsonNumber(asLong, value, true);
        }
        return new JsonNumber(asLong, value, false);
    }

    /**
     * Returns whether the number has no fractional part.
     *
     * @return {@code true} for integral numbers
     */
    public boolean isIntegral() {
        return integral;
    }

    /**
     * Returns the value as {@code long}.
     *
     * @return the value, truncated if fractional
     */
    public long longValue() {
        return longValue;
    }

    /**
     * Returns the value as {@code int}.
     *
     * @return the value, truncated if fractional and narrowed if too large
     */
    public int intValue() {
        return (int) longValue;
    }

    /**
     * Returns the value as {@code double}.
     *
     * @return the value, possibly rounded for very large integers
     */
    public double doubleValue() {
        return doubleValue;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof JsonNumber n)) {
            return false;
        }
        return integral && n.integral ? longValue == n.longValue : Double.compare(doubleValue, n.doubleValue) == 0;
    }

    @Override
    public int hashCode() {
        return integral ? Long.hashCode(longValue) : Double.hashCode(doubleValue);
    }

    /**
     * Returns the JSON text of the number.
     *
     * @return for example {@code 42} or {@code 7.5}
     */
    @Override
    public String toString() {
        return integral ? Long.toString(longValue) : Double.toString(doubleValue);
    }
}
