package dev.gulp.api.render;

import dev.gulp.api.graphics.Shader;

/**
 * A post-processing step with your own fragment shader. The shader samples the picture from {@code u_texture} at
 * {@code v_texCoord} and gets {@code u_resolution} (pixels) and {@code u_time} (seconds); set other uniforms on the
 * shader itself.
 *
 * <pre>{@code
 * Shader waves = graphics().shader("""
 *         in vec2 v_texCoord; uniform sampler2D u_texture; uniform float u_time; out vec4 fragColor;
 *         void main() { fragColor = texture(u_texture, v_texCoord + vec2(sin(v_texCoord.y * 40.0 + u_time) * 0.003, 0.0)); }
 *         """);
 * world.postEffects().add(new CustomEffect(waves));
 * }</pre>
 */
public final class CustomEffect extends PostEffect {

    private final Shader shader;

    /**
     * Creates the effect.
     *
     * @param shader a fragment shader built with {@code graphics().shader(fragment)}
     */
    public CustomEffect(Shader shader) {
        this.shader = shader;
    }

    /**
     * Returns the shader.
     *
     * @return the shader
     */
    public Shader shader() {
        return shader;
    }
}
