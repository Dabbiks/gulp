package dev.gulp.core.graphics;

/** Fragment shaders of the light map and the built-in post-processing effects. */
final class PostShaders {

    /** Light fan: texture coordinates are the offset from the light in radii; {@code v_params.a} is falloff / 4. */
    static final String LIGHT = """
            #include "gulp:common.glsl"
            void main() {
                float d = length(v_texCoord);
                float fade = pow(clamp(1.0 - d, 0.0, 1.0), max(v_params.a * 4.0, 0.05));
                fragColor = vec4(v_color.rgb * fade, 0.0);
            }
            """;

    static final String BRIGHT = """
            #include "gulp:common.glsl"
            uniform float u_threshold;
            uniform float u_intensity;
            void main() {
                vec4 c = texture(u_texture, v_texCoord);
                float l = max(c.r, max(c.g, c.b));
                fragColor = c * smoothstep(u_threshold, u_threshold + 0.1, l) * u_intensity;
            }
            """;

    /** Nine-tap Gaussian along {@code u_direction}, given in texels. */
    static final String BLUR = """
            #include "gulp:common.glsl"
            uniform vec2 u_direction;
            void main() {
                vec2 step = u_direction / vec2(textureSize(u_texture, 0));
                vec4 sum = texture(u_texture, v_texCoord) * 0.2270270270;
                sum += texture(u_texture, v_texCoord + step) * 0.1945945946;
                sum += texture(u_texture, v_texCoord - step) * 0.1945945946;
                sum += texture(u_texture, v_texCoord + step * 2.0) * 0.1216216216;
                sum += texture(u_texture, v_texCoord - step * 2.0) * 0.1216216216;
                sum += texture(u_texture, v_texCoord + step * 3.0) * 0.0540540541;
                sum += texture(u_texture, v_texCoord - step * 3.0) * 0.0540540541;
                sum += texture(u_texture, v_texCoord + step * 4.0) * 0.0162162162;
                sum += texture(u_texture, v_texCoord - step * 4.0) * 0.0162162162;
                fragColor = sum;
            }
            """;

    static final String VIGNETTE = """
            #include "gulp:common.glsl"
            uniform float u_intensity;
            uniform float u_radius;
            uniform float u_softness;
            uniform vec4 u_color;
            void main() {
                vec4 c = texture(u_texture, v_texCoord);
                float d = length(v_texCoord - 0.5) * 1.41421356;
                float f = smoothstep(u_radius - u_softness, u_radius, d) * u_intensity;
                c.rgb = mix(c.rgb, u_color.rgb * c.a, clamp(f, 0.0, 1.0));
                fragColor = c;
            }
            """;

    /** A 256 x 16 LUT: 16 slices of blue, each 16 x 16 of red (x) and green (y), on unit 1. */
    static final String COLOR_GRADE = """
            #include "gulp:common.glsl"
            uniform sampler2D u_lut;
            uniform float u_intensity;
            void main() {
                vec4 c = texture(u_texture, v_texCoord);
                vec3 col = clamp(c.rgb / max(c.a, 0.0001), 0.0, 1.0);
                float b = col.b * 15.0;
                float s0 = floor(b);
                float s1 = min(s0 + 1.0, 15.0);
                float y = (col.g * 15.0 + 0.5) / 16.0;
                vec3 g0 = texture(u_lut, vec2((s0 * 16.0 + col.r * 15.0 + 0.5) / 256.0, y)).rgb;
                vec3 g1 = texture(u_lut, vec2((s1 * 16.0 + col.r * 15.0 + 0.5) / 256.0, y)).rgb;
                vec3 graded = mix(g0, g1, b - s0);
                c.rgb = mix(col, graded, u_intensity) * c.a;
                fragColor = c;
            }
            """;

    static final String PIXELATE = """
            #include "gulp:common.glsl"
            uniform float u_size;
            uniform vec2 u_resolution;
            void main() {
                vec2 cell = max(u_size, 1.0) / u_resolution;
                fragColor = texture(u_texture, (floor(v_texCoord / cell) + 0.5) * cell);
            }
            """;

    static final String CHROMATIC = """
            #include "gulp:common.glsl"
            uniform float u_amount;
            uniform vec2 u_resolution;
            void main() {
                vec2 shift = (v_texCoord - 0.5) * 2.0 * u_amount / u_resolution;
                vec4 c = texture(u_texture, v_texCoord);
                c.r = texture(u_texture, v_texCoord + shift).r;
                c.b = texture(u_texture, v_texCoord - shift).b;
                fragColor = c;
            }
            """;

    static final String CRT = """
            #include "gulp:common.glsl"
            uniform float u_curvature;
            uniform float u_scanlines;
            uniform vec2 u_resolution;
            void main() {
                vec2 p = v_texCoord * 2.0 - 1.0;
                p += p * (p.yx * p.yx) * u_curvature;
                vec2 uv = p * 0.5 + 0.5;
                if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                    fragColor = vec4(0.0, 0.0, 0.0, 1.0);
                    return;
                }
                vec4 c = texture(u_texture, uv);
                float line = 0.5 + 0.5 * sin(uv.y * u_resolution.y * 3.14159265);
                c.rgb *= 1.0 - u_scanlines * (1.0 - line);
                fragColor = c;
            }
            """;

    private PostShaders() {}
}
