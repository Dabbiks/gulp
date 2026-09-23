package dev.gulp.api.math;

import java.util.List;
import java.util.Map;

/**
 * Fast seeded random generator (xoshiro256**). Instances are independent, so each world can have its own for
 * deterministic replays. Not thread-safe.
 *
 * <pre>{@code
 * Rng rng = new Rng(seed);
 * int damage = rng.nextInt(3, 7);                 // 3..7 inclusive
 * if (rng.chance(0.1f)) dropRareItem();
 * Item loot = rng.weighted(Map.of(common, 90f, rare, 10f));
 * }</pre>
 */
public final class Rng {

    private long s0;
    private long s1;
    private long s2;
    private long s3;

    /**
     * Creates a generator from a seed.
     *
     * @param seed the seed
     */
    public Rng(long seed) {
        setSeed(seed);
    }

    /** Creates a generator seeded from the clock. */
    public Rng() {
        this(System.nanoTime() ^ 0x5DEECE66DL);
    }

    /**
     * Resets the sequence.
     *
     * @param seed the seed
     */
    public void setSeed(long seed) {
        long x = seed;
        x += 0x9E3779B97F4A7C15L;
        s0 = mix(x);
        x += 0x9E3779B97F4A7C15L;
        s1 = mix(x);
        x += 0x9E3779B97F4A7C15L;
        s2 = mix(x);
        x += 0x9E3779B97F4A7C15L;
        s3 = mix(x);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * Next 64 random bits.
     *
     * @return a random long
     */
    public long nextLong() {
        long result = Long.rotateLeft(s1 * 5, 7) * 9;
        long t = s1 << 17;
        s2 ^= s0;
        s3 ^= s1;
        s1 ^= s2;
        s0 ^= s3;
        s2 ^= t;
        s3 = Long.rotateLeft(s3, 45);
        return result;
    }

    /**
     * Random int over the whole range.
     *
     * @return a random int
     */
    public int nextInt() {
        return (int) (nextLong() >>> 32);
    }

    /**
     * Random int in {@code 0..bound-1}.
     *
     * @param bound exclusive upper bound, positive
     * @return a random int
     * @throws IllegalArgumentException if the bound is not positive
     */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive: " + bound);
        }
        return (int) (((nextLong() >>> 33) * bound) >>> 31);
    }

    /**
     * Random int in {@code min..max} inclusive.
     *
     * @param min smallest value
     * @param max largest value
     * @return a random int
     */
    public int nextInt(int min, int max) {
        if (max < min) {
            throw new IllegalArgumentException("max < min: " + max + " < " + min);
        }
        return min + (int) ((nextLong() >>> 1) % ((long) max - min + 1));
    }

    /**
     * Random float in {@code [0, 1)}.
     *
     * @return a random float
     */
    public float nextFloat() {
        return (nextLong() >>> 40) * 0x1.0p-24f;
    }

    /**
     * Random float in {@code [min, max)}.
     *
     * @param min lower bound
     * @param max upper bound
     * @return a random float
     */
    public float nextFloat(float min, float max) {
        return min + nextFloat() * (max - min);
    }

    /**
     * Random double in {@code [0, 1)}.
     *
     * @return a random double
     */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    /**
     * Random boolean.
     *
     * @return {@code true} half of the time
     */
    public boolean nextBoolean() {
        return nextLong() < 0;
    }

    /**
     * Returns {@code true} with a probability.
     *
     * @param probability {@code 0..1}
     * @return {@code true} with that probability
     */
    public boolean chance(float probability) {
        return nextFloat() < probability;
    }

    /**
     * Picks a random element.
     *
     * @param <T> the element type
     * @param items the choices, not empty
     * @return one of them
     * @throws IllegalArgumentException if the list is empty
     */
    public <T> T pick(List<T> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Nothing to pick from");
        }
        return items.get(nextInt(items.size()));
    }

    /**
     * Picks a key with probability proportional to its weight.
     *
     * @param <T> the key type
     * @param weights keys and non-negative weights, total above zero
     * @return the chosen key
     * @throws IllegalArgumentException if the total weight is not positive
     */
    public <T> T weighted(Map<T, Float> weights) {
        float total = 0f;
        for (float weight : weights.values()) {
            total += Math.max(0f, weight);
        }
        if (total <= 0f) {
            throw new IllegalArgumentException("Total weight must be positive");
        }
        float roll = nextFloat() * total;
        T last = null;
        for (Map.Entry<T, Float> entry : weights.entrySet()) {
            float weight = Math.max(0f, entry.getValue());
            if (weight == 0f) {
                continue;
            }
            last = entry.getKey();
            roll -= weight;
            if (roll < 0f) {
                return last;
            }
        }
        return last;
    }

    /**
     * Shuffles a list in place (Fisher-Yates).
     *
     * @param <T> the element type
     * @param items a mutable list
     */
    public <T> void shuffle(List<T> items) {
        for (int i = items.size() - 1; i > 0; i--) {
            int j = nextInt(i + 1);
            T swap = items.get(i);
            items.set(i, items.get(j));
            items.set(j, swap);
        }
    }

    /**
     * Random point inside a circle around the origin, uniformly distributed.
     *
     * @param radius the radius
     * @return the point
     */
    public Vec2 insideCircle(float radius) {
        float r = radius * (float) Math.sqrt(nextFloat());
        return Vec2.fromAngle(nextFloat() * 360f).scale(r);
    }

    /**
     * Random point on a circle around the origin.
     *
     * @param radius the radius
     * @return the point
     */
    public Vec2 onCircle(float radius) {
        return Vec2.fromAngle(nextFloat() * 360f).scale(radius);
    }
}
