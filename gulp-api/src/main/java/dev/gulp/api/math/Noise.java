package dev.gulp.api.math;

/**
 * Seeded gradient noise for procedural worlds: Perlin and simplex in 1D, 2D and 3D, fractal sums of octaves and domain
 * warping. Values are roughly in {@code -1..1}. Instances are immutable and thread-safe.
 *
 * <pre>{@code
 * Noise noise = new Noise(worldSeed);
 * float height = noise.fractal(Noise.Type.SIMPLEX, x * 0.01f, y * 0.01f, 5, 2f, 0.5f);
 * Vec2 warped = noise.warp(x, y, 0.02f, 20f);
 * }</pre>
 */
public final class Noise {

    /** Noise algorithm. */
    public enum Type {
        /** Improved Perlin noise. */
        PERLIN,
        /** Simplex noise: fewer directional artifacts, cheaper in 3D. */
        SIMPLEX
    }

    private static final float F2 = 0.5f * ((float) Math.sqrt(3) - 1f);
    private static final float G2 = (3f - (float) Math.sqrt(3)) / 6f;
    private static final float F3 = 1f / 3f;
    private static final float G3 = 1f / 6f;
    private static final int[][] GRAD3 = {
        {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
        {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
        {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}
    };

    private final int[] perm = new int[512];

    /**
     * Creates noise for a seed.
     *
     * @param seed the seed
     */
    public Noise(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        Rng rng = new Rng(seed);
        for (int i = 255; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int swap = p[i];
            p[i] = p[j];
            p[j] = swap;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
        }
    }

    /**
     * 1D Perlin noise.
     *
     * @param x coordinate
     * @return noise value
     */
    public float perlin(float x) {
        return perlin(x, 0.5f, 0.5f);
    }

    /**
     * 2D Perlin noise.
     *
     * @param x coordinate
     * @param y coordinate
     * @return noise value
     */
    public float perlin(float x, float y) {
        return perlin(x, y, 0.5f);
    }

    /**
     * 3D Perlin noise.
     *
     * @param x coordinate
     * @param y coordinate
     * @param z coordinate
     * @return noise value
     */
    public float perlin(float x, float y, float z) {
        int xi = floor(x);
        int yi = floor(y);
        int zi = floor(z);
        float xf = x - xi;
        float yf = y - yi;
        float zf = z - zi;
        int X = xi & 255;
        int Y = yi & 255;
        int Z = zi & 255;
        float u = fade(xf);
        float v = fade(yf);
        float w = fade(zf);
        int a = perm[X] + Y;
        int aa = perm[a] + Z;
        int ab = perm[a + 1] + Z;
        int b = perm[X + 1] + Y;
        int ba = perm[b] + Z;
        int bb = perm[b + 1] + Z;
        float x1 = Mathf.lerp(grad(perm[aa], xf, yf, zf), grad(perm[ba], xf - 1, yf, zf), u);
        float x2 = Mathf.lerp(grad(perm[ab], xf, yf - 1, zf), grad(perm[bb], xf - 1, yf - 1, zf), u);
        float y1 = Mathf.lerp(x1, x2, v);
        float x3 = Mathf.lerp(grad(perm[aa + 1], xf, yf, zf - 1), grad(perm[ba + 1], xf - 1, yf, zf - 1), u);
        float x4 = Mathf.lerp(grad(perm[ab + 1], xf, yf - 1, zf - 1), grad(perm[bb + 1], xf - 1, yf - 1, zf - 1), u);
        float y2 = Mathf.lerp(x3, x4, v);
        return Mathf.lerp(y1, y2, w);
    }

    /**
     * 1D simplex noise.
     *
     * @param x coordinate
     * @return noise value
     */
    public float simplex(float x) {
        return simplex(x, 0.5f);
    }

    /**
     * 2D simplex noise.
     *
     * @param x coordinate
     * @param y coordinate
     * @return noise value
     */
    public float simplex(float x, float y) {
        float s = (x + y) * F2;
        int i = floor(x + s);
        int j = floor(y + s);
        float t = (i + j) * G2;
        float x0 = x - (i - t);
        float y0 = y - (j - t);
        int i1 = x0 > y0 ? 1 : 0;
        int j1 = 1 - i1;
        float x1 = x0 - i1 + G2;
        float y1 = y0 - j1 + G2;
        float x2 = x0 - 1f + 2f * G2;
        float y2 = y0 - 1f + 2f * G2;
        int ii = i & 255;
        int jj = j & 255;
        float n = corner2(perm[ii + perm[jj]] % 12, x0, y0)
                + corner2(perm[ii + i1 + perm[jj + j1]] % 12, x1, y1)
                + corner2(perm[ii + 1 + perm[jj + 1]] % 12, x2, y2);
        return 70f * n;
    }

    /**
     * 3D simplex noise.
     *
     * @param x coordinate
     * @param y coordinate
     * @param z coordinate
     * @return noise value
     */
    public float simplex(float x, float y, float z) {
        float s = (x + y + z) * F3;
        int i = floor(x + s);
        int j = floor(y + s);
        int k = floor(z + s);
        float t = (i + j + k) * G3;
        float x0 = x - (i - t);
        float y0 = y - (j - t);
        float z0 = z - (k - t);
        int i1;
        int j1;
        int k1;
        int i2;
        int j2;
        int k2;
        if (x0 >= y0) {
            if (y0 >= z0) {
                i1 = 1;
                j1 = 0;
                k1 = 0;
                i2 = 1;
                j2 = 1;
                k2 = 0;
            } else if (x0 >= z0) {
                i1 = 1;
                j1 = 0;
                k1 = 0;
                i2 = 1;
                j2 = 0;
                k2 = 1;
            } else {
                i1 = 0;
                j1 = 0;
                k1 = 1;
                i2 = 1;
                j2 = 0;
                k2 = 1;
            }
        } else if (y0 < z0) {
            i1 = 0;
            j1 = 0;
            k1 = 1;
            i2 = 0;
            j2 = 1;
            k2 = 1;
        } else if (x0 < z0) {
            i1 = 0;
            j1 = 1;
            k1 = 0;
            i2 = 0;
            j2 = 1;
            k2 = 1;
        } else {
            i1 = 0;
            j1 = 1;
            k1 = 0;
            i2 = 1;
            j2 = 1;
            k2 = 0;
        }
        float x1 = x0 - i1 + G3;
        float y1 = y0 - j1 + G3;
        float z1 = z0 - k1 + G3;
        float x2 = x0 - i2 + 2f * G3;
        float y2 = y0 - j2 + 2f * G3;
        float z2 = z0 - k2 + 2f * G3;
        float x3 = x0 - 1f + 3f * G3;
        float y3 = y0 - 1f + 3f * G3;
        float z3 = z0 - 1f + 3f * G3;
        int ii = i & 255;
        int jj = j & 255;
        int kk = k & 255;
        float n = corner3(perm[ii + perm[jj + perm[kk]]] % 12, x0, y0, z0)
                + corner3(perm[ii + i1 + perm[jj + j1 + perm[kk + k1]]] % 12, x1, y1, z1)
                + corner3(perm[ii + i2 + perm[jj + j2 + perm[kk + k2]]] % 12, x2, y2, z2)
                + corner3(perm[ii + 1 + perm[jj + 1 + perm[kk + 1]]] % 12, x3, y3, z3);
        return 32f * n;
    }

    /**
     * Fractal (fBm) 2D noise: a sum of octaves with rising frequency and falling amplitude, normalized to about
     * {@code -1..1}.
     *
     * @param type the base noise
     * @param x coordinate
     * @param y coordinate
     * @param octaves number of layers, at least 1
     * @param lacunarity frequency multiplier per octave, usually 2
     * @param gain amplitude multiplier per octave, usually 0.5
     * @return noise value
     */
    public float fractal(Type type, float x, float y, int octaves, float lacunarity, float gain) {
        float sum = 0f;
        float amplitude = 1f;
        float frequency = 1f;
        float norm = 0f;
        for (int o = 0; o < Math.max(1, octaves); o++) {
            float value =
                    type == Type.PERLIN ? perlin(x * frequency, y * frequency) : simplex(x * frequency, y * frequency);
            sum += value * amplitude;
            norm += amplitude;
            amplitude *= gain;
            frequency *= lacunarity;
        }
        return sum / norm;
    }

    /**
     * Fractal (fBm) 3D noise.
     *
     * @param type the base noise
     * @param x coordinate
     * @param y coordinate
     * @param z coordinate
     * @param octaves number of layers, at least 1
     * @param lacunarity frequency multiplier per octave
     * @param gain amplitude multiplier per octave
     * @return noise value
     */
    public float fractal(Type type, float x, float y, float z, int octaves, float lacunarity, float gain) {
        float sum = 0f;
        float amplitude = 1f;
        float frequency = 1f;
        float norm = 0f;
        for (int o = 0; o < Math.max(1, octaves); o++) {
            float fx = x * frequency;
            float fy = y * frequency;
            float fz = z * frequency;
            sum += (type == Type.PERLIN ? perlin(fx, fy, fz) : simplex(fx, fy, fz)) * amplitude;
            norm += amplitude;
            amplitude *= gain;
            frequency *= lacunarity;
        }
        return sum / norm;
    }

    /**
     * Domain warp: offsets a point by noise, for organic, swirly shapes.
     *
     * @param x coordinate
     * @param y coordinate
     * @param frequency frequency of the warping noise
     * @param strength largest offset
     * @return the warped point
     */
    public Vec2 warp(float x, float y, float frequency, float strength) {
        float dx = simplex(x * frequency, y * frequency);
        float dy = simplex(x * frequency + 31.7f, y * frequency + 47.3f);
        return new Vec2(x + dx * strength, y + dy * strength);
    }

    private static int floor(float value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    private static float fade(float t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static float grad(int hash, float x, float y, float z) {
        int[] g = GRAD3[hash % 12];
        return g[0] * x + g[1] * y + g[2] * z;
    }

    private static float corner2(int gradient, float x, float y) {
        float t = 0.5f - x * x - y * y;
        if (t < 0f) {
            return 0f;
        }
        t *= t;
        int[] g = GRAD3[gradient];
        return t * t * (g[0] * x + g[1] * y);
    }

    private static float corner3(int gradient, float x, float y, float z) {
        float t = 0.6f - x * x - y * y - z * z;
        if (t < 0f) {
            return 0f;
        }
        t *= t;
        int[] g = GRAD3[gradient];
        return t * t * (g[0] * x + g[1] * y + g[2] * z);
    }
}
