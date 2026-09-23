package dev.gulp.core.graphics;

import dev.gulp.api.Logger;
import dev.gulp.api.Owner;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.FrameBuffer;
import dev.gulp.api.graphics.Graphics;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.graphics.Shader;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.graphics.TextureFilter;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.scheduler.Promise;
import dev.gulp.core.CoreContext;
import dev.gulp.core.MainQueue;
import dev.gulp.core.scheduler.PromiseImpl;
import dev.gulp.platform.DecodedImage;
import dev.gulp.platform.Gl;
import dev.gulp.platform.PlatformCallback;
import dev.gulp.platform.PlatformDecoders;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** {@link Graphics}: creates GPU resources and frees whatever is left when the engine stops. */
public final class GraphicsImpl implements Graphics {

    private final Gl gl;
    private final PlatformDecoders decoders;
    private final CoreContext context;
    private final MainQueue mainQueue;
    private final Owner owner;
    private final Logger logger;
    private final Map<String, String> includes = new HashMap<>();
    private final List<TextureImpl> textures = new ArrayList<>();
    private final List<ShaderImpl> shaders = new ArrayList<>();
    private final List<FrameBufferImpl> frameBuffers = new ArrayList<>();
    private final ShaderImpl defaultShader;
    private final TextureImpl white;
    private final TextureImpl fallback;

    /**
     * Creates the factory and the built-in resources.
     *
     * @param gl the graphics context
     * @param decoders image decoders
     * @param context loggers and owner state
     * @param mainQueue for decode results
     * @param owner owner of decode promises (the game)
     * @param logger where shader errors go
     */
    public GraphicsImpl(
            Gl gl, PlatformDecoders decoders, CoreContext context, MainQueue mainQueue, Owner owner, Logger logger) {
        this.gl = gl;
        this.decoders = decoders;
        this.context = context;
        this.mainQueue = mainQueue;
        this.owner = owner;
        this.logger = logger;
        includes.put("gulp:common.glsl", ShaderImpl.COMMON_INCLUDE);
        this.defaultShader =
                new ShaderImpl(gl, ShaderImpl.DEFAULT_VERTEX, ShaderImpl.DEFAULT_FRAGMENT, includes, logger);
        Pixmap one = new Pixmap(1, 1);
        one.fill(Color.WHITE);
        this.white = new TextureImpl(gl, 1, 1, TextureFilter.NEAREST, one);
        Pixmap checker = new Pixmap(8, 8);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                checker.setPixel(x, y, ((x / 4 + y / 4) % 2 == 0) ? Color.MAGENTA : Color.BLACK);
            }
        }
        this.fallback = new TextureImpl(gl, 8, 8, TextureFilter.NEAREST, checker);
    }

    ShaderImpl defaultShader() {
        return defaultShader;
    }

    TextureImpl whiteTexture() {
        return white;
    }

    TextureImpl fallbackTexture() {
        return fallback;
    }

    Gl gl() {
        return gl;
    }

    @Override
    public Texture texture(Pixmap pixmap) {
        return texture(pixmap, TextureFilter.LINEAR);
    }

    @Override
    public Texture texture(Pixmap pixmap, TextureFilter filter) {
        context.checkMainThread("Graphics.texture");
        TextureImpl texture = new TextureImpl(gl, pixmap.width(), pixmap.height(), filter, pixmap);
        textures.add(texture);
        return texture;
    }

    /**
     * Creates an empty texture, for render targets.
     *
     * @param width width in pixels
     * @param height height in pixels
     * @param filter the filter
     * @return the texture
     */
    TextureImpl emptyTexture(int width, int height, TextureFilter filter) {
        TextureImpl texture = new TextureImpl(gl, width, height, filter, null);
        textures.add(texture);
        return texture;
    }

    @Override
    public Promise<Pixmap> decode(byte[] encoded) {
        PromiseImpl<Pixmap> promise = new PromiseImpl<>(owner, context, mainQueue);
        ByteBuffer buffer = ByteBuffer.allocateDirect(encoded.length).order(ByteOrder.nativeOrder());
        buffer.put(encoded).flip();
        decoders.decodeImage(buffer, new PlatformCallback<>() {
            @Override
            public void success(DecodedImage image) {
                byte[] rgba = new byte[image.width() * image.height() * 4];
                image.pixels().duplicate().get(rgba);
                promise.complete(Pixmap.fromRgba(image.width(), image.height(), rgba));
            }

            @Override
            public void failure(Throwable error) {
                promise.fail(error);
            }
        });
        return promise;
    }

    @Override
    public Shader shader(String fragmentSource) {
        return shader(ShaderImpl.DEFAULT_VERTEX, fragmentSource);
    }

    @Override
    public Shader shader(String vertexSource, String fragmentSource) {
        context.checkMainThread("Graphics.shader");
        ShaderImpl shader = new ShaderImpl(gl, vertexSource, fragmentSource, includes, logger);
        shaders.add(shader);
        return shader;
    }

    @Override
    public void include(String name, String source) {
        if (name.startsWith("gulp:")) {
            throw new IllegalArgumentException("Include names starting with 'gulp:' are reserved: " + name);
        }
        includes.put(name, source);
    }

    @Override
    public FrameBuffer frameBuffer(int width, int height) {
        return frameBuffer(width, height, false);
    }

    @Override
    public FrameBuffer frameBuffer(int width, int height, boolean stencil) {
        return frameBuffer(width, height, stencil, TextureFilter.LINEAR);
    }

    /**
     * Creates a frame buffer with a chosen filter.
     *
     * @param width width in pixels
     * @param height height in pixels
     * @param stencil whether to attach a stencil buffer
     * @param filter filter of the color texture
     * @return the frame buffer
     */
    FrameBufferImpl frameBuffer(int width, int height, boolean stencil, TextureFilter filter) {
        context.checkMainThread("Graphics.frameBuffer");
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Frame buffer size must be positive: " + width + "x" + height);
        }
        FrameBufferImpl buffer = new FrameBufferImpl(gl, width, height, stencil, filter);
        frameBuffers.add(buffer);
        return buffer;
    }

    @Override
    public TextureRegion white() {
        return white.region();
    }

    @Override
    public int maxTextureSize() {
        return gl.getInteger(Gl.MAX_TEXTURE_SIZE);
    }

    /** Frees every resource created through this factory. */
    public void disposeAll() {
        for (FrameBufferImpl buffer : frameBuffers) {
            buffer.dispose();
        }
        for (ShaderImpl shader : shaders) {
            shader.dispose();
        }
        for (TextureImpl texture : textures) {
            texture.dispose();
        }
        frameBuffers.clear();
        shaders.clear();
        textures.clear();
        defaultShader.dispose();
        white.dispose();
        fallback.dispose();
    }
}
