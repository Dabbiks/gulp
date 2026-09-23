package dev.gulp.api.graphics;

import dev.gulp.api.scheduler.Promise;

/**
 * Creates GPU resources: textures, shaders and frame buffers. Asset loading with reference counting arrives in stage 3;
 * until then resources are created here and freed with {@code dispose()} (or automatically when the game stops).
 *
 * <pre>{@code
 * graphics().decode(pngBytes).thenSync(pixmap -> player = graphics().texture(pixmap));
 * Shader glow = graphics().shader(glowFragmentSource);
 * FrameBuffer canvas = graphics().frameBuffer(320, 180);
 * }</pre>
 */
public interface Graphics {

    /**
     * Uploads a pixmap as a texture with linear filtering.
     *
     * @param pixmap the pixels
     * @return the texture
     */
    Texture texture(Pixmap pixmap);

    /**
     * Uploads a pixmap as a texture.
     *
     * @param pixmap the pixels
     * @param filter the sampling filter
     * @return the texture
     */
    Texture texture(Pixmap pixmap, TextureFilter filter);

    /**
     * Decodes PNG, JPEG or WebP bytes.
     *
     * @param encoded the file bytes
     * @return the pixmap, delivered on the main thread
     */
    Promise<Pixmap> decode(byte[] encoded);

    /**
     * Creates a shader from a fragment source; the default vertex shader is used.
     *
     * @param fragmentSource GLSL ES 3.00 fragment source
     * @return the shader (the default shader if compilation failed; see {@link Shader#isValid()})
     */
    Shader shader(String fragmentSource);

    /**
     * Creates a shader from both sources.
     *
     * @param vertexSource GLSL ES 3.00 vertex source
     * @param fragmentSource GLSL ES 3.00 fragment source
     * @return the shader
     */
    Shader shader(String vertexSource, String fragmentSource);

    /**
     * Registers source text for {@code #include "name"} in shaders. The {@code gulp:} prefix is reserved.
     *
     * @param name the include name, for example {@code "coins:lighting.glsl"}
     * @param source the text
     * @throws IllegalArgumentException if the name starts with {@code gulp:}
     */
    void include(String name, String source);

    /**
     * Creates a frame buffer.
     *
     * @param width width in pixels
     * @param height height in pixels
     * @return the frame buffer
     */
    FrameBuffer frameBuffer(int width, int height);

    /**
     * Creates a frame buffer with a stencil buffer.
     *
     * @param width width in pixels
     * @param height height in pixels
     * @param stencil whether to attach a depth-stencil buffer
     * @return the frame buffer
     */
    FrameBuffer frameBuffer(int width, int height, boolean stencil);

    /**
     * A 1x1 white region, handy for drawing solid shapes with textures' code paths.
     *
     * @return the white region
     */
    TextureRegion white();

    /**
     * Largest texture side the GPU supports.
     *
     * @return pixels
     */
    int maxTextureSize();
}
