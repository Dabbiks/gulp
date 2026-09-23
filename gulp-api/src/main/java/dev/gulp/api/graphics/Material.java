package dev.gulp.api.graphics;

import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * How things are drawn: a shader (or the default one), a blend mode and extra textures bound to units 1 and up.
 * Immutable; the {@code with*} methods return copies.
 *
 * <pre>{@code
 * Material glow = Material.DEFAULT.withBlend(BlendMode.ADD);
 * Material water = Material.of(waveShader).withTexture(1, noiseTexture);
 * draw.material(glow);
 * }</pre>
 */
public final class Material {

    /** Default shader and normal blending. */
    public static final Material DEFAULT = new Material(null, BlendMode.NORMAL, Map.of());

    private final @Nullable Shader shader;
    private final BlendMode blend;
    private final Map<Integer, Texture> textures;

    private Material(@Nullable Shader shader, BlendMode blend, Map<Integer, Texture> textures) {
        this.shader = shader;
        this.blend = blend;
        this.textures = textures;
    }

    /**
     * A material with a custom shader and normal blending.
     *
     * @param shader the shader
     * @return the material
     */
    public static Material of(Shader shader) {
        return new Material(shader, BlendMode.NORMAL, Map.of());
    }

    /**
     * The shader.
     *
     * @return the shader, or {@code null} for the default one
     */
    public @Nullable Shader shader() {
        return shader;
    }

    /**
     * The blend mode.
     *
     * @return the blend mode
     */
    public BlendMode blend() {
        return blend;
    }

    /**
     * Extra textures by unit.
     *
     * @return an unmodifiable map from unit (1 and up) to texture
     */
    public Map<Integer, Texture> textures() {
        return textures;
    }

    /**
     * Copy with another shader.
     *
     * @param newShader the shader, or {@code null} for the default one
     * @return the new material
     */
    public Material withShader(@Nullable Shader newShader) {
        return new Material(newShader, blend, textures);
    }

    /**
     * Copy with another blend mode.
     *
     * @param newBlend the blend mode
     * @return the new material
     */
    public Material withBlend(BlendMode newBlend) {
        return new Material(shader, newBlend, textures);
    }

    /**
     * Copy with an extra texture, available to the shader as a sampler set to that unit.
     *
     * @param unit the texture unit, 1 to 7
     * @param texture the texture
     * @return the new material
     * @throws IllegalArgumentException if the unit is out of range
     */
    public Material withTexture(int unit, Texture texture) {
        if (unit < 1 || unit > 7) {
            throw new IllegalArgumentException("Texture unit must be 1..7, got " + unit);
        }
        Map<Integer, Texture> copy = new TreeMap<>(textures);
        copy.put(unit, texture);
        return new Material(shader, blend, java.util.Collections.unmodifiableMap(copy));
    }
}
