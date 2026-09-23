package dev.gulp.api.graphics;

import dev.gulp.api.math.Mat3;
import dev.gulp.api.math.Vec2;

/**
 * A GPU program in GLSL ES 3.00. Sources may use {@code #include "gulp:common.glsl"}, which declares the engine's
 * inputs ({@code v_texCoord}, {@code v_color}, {@code u_texture}) and output ({@code fragColor}). A shader that fails
 * to compile is logged with line numbers and replaced by the default shader.
 *
 * <pre>{@code
 * Shader wave = graphics().shader("""
 *         #include "gulp:common.glsl"
 *         uniform float u_time;
 *         void main() {
 *             vec2 uv = v_texCoord + vec2(sin(u_time + v_texCoord.y * 20.0) * 0.01, 0.0);
 *             fragColor = texture(u_texture, uv) * v_color;
 *         }
 *         """);
 * wave.set("u_time", seconds);
 * draw.material(Material.of(wave));
 * }</pre>
 */
public interface Shader {

    /**
     * Sets an int or sampler uniform; kept and applied whenever the shader is used.
     *
     * @param name the uniform name
     * @param value the value
     * @return this shader
     */
    Shader set(String name, int value);

    /**
     * Sets a float uniform.
     *
     * @param name the uniform name
     * @param value the value
     * @return this shader
     */
    Shader set(String name, float value);

    /**
     * Sets a vec2 uniform.
     *
     * @param name the uniform name
     * @param value the value
     * @return this shader
     */
    Shader set(String name, Vec2 value);

    /**
     * Sets a vec4 uniform from a color (straight alpha).
     *
     * @param name the uniform name
     * @param value the value
     * @return this shader
     */
    Shader set(String name, Color value);

    /**
     * Sets a mat3 uniform.
     *
     * @param name the uniform name
     * @param value the value
     * @return this shader
     */
    Shader set(String name, Mat3 value);

    /**
     * Returns whether the shader compiled; if not, the default shader is used in its place.
     *
     * @return {@code true} if compiled and linked
     */
    boolean isValid();

    /**
     * Returns the compiler and linker messages.
     *
     * @return the log, empty when there were no messages
     */
    String log();

    /** Frees the GPU program. */
    void dispose();
}
