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
import dev.gulp.api.text.Font;
import dev.gulp.api.text.FontFamily;
import dev.gulp.api.text.Text;
import dev.gulp.api.text.TextBox;
import dev.gulp.api.text.TextLayout;
import dev.gulp.api.text.TextStyle;
import dev.gulp.core.CoreContext;
import dev.gulp.core.MainQueue;
import dev.gulp.core.scheduler.PromiseImpl;
import dev.gulp.core.text.BitmapFontImpl;
import dev.gulp.core.text.TextLayoutImpl;
import dev.gulp.core.text.TextSystem;
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
import org.jspecify.annotations.Nullable;

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
    private final ShaderImpl msdfShader;
    private final Map<String, ShaderImpl> builtIn = new HashMap<>();
    private @Nullable TextureImpl softDot;
    private @Nullable TextSystem textSystem;
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
        this.msdfShader = new ShaderImpl(gl, ShaderImpl.DEFAULT_VERTEX, ShaderImpl.MSDF_FRAGMENT, includes, logger);
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

    ShaderImpl msdfShader() {
        return msdfShader;
    }

    /**
     * Returns the shader of a built-in material, compiled on first use.
     *
     * @param name the material name, such as {@code gulp:flash}
     * @return the shader, or the default one for an unknown name
     */
    ShaderImpl builtInShader(String name) {
        ShaderImpl shader = builtIn.get(name);
        if (shader != null) {
            return shader;
        }
        String fragment =
                switch (name) {
                    case "gulp:flash" -> ShaderImpl.FLASH_FRAGMENT;
                    case "gulp:outline" -> ShaderImpl.OUTLINE_FRAGMENT;
                    case "gulp:dissolve" -> ShaderImpl.DISSOLVE_FRAGMENT;
                    case "gulp:grayscale" -> ShaderImpl.GRAYSCALE_FRAGMENT;
                    case "gulp:tint" -> ShaderImpl.TINT_FRAGMENT;
                    default -> null;
                };
        shader = fragment == null
                ? defaultShader
                : new ShaderImpl(gl, ShaderImpl.DEFAULT_VERTEX, fragment, includes, logger);
        builtIn.put(name, shader);
        return shader;
    }

    /**
     * Connects text layout.
     *
     * @param system the text system
     */
    public void setTextSystem(TextSystem system) {
        this.textSystem = system;
    }

    @Override
    public TextLayout layout(Text text, TextStyle style, TextBox box) {
        TextSystem system = textSystem;
        TextLayoutImpl layout = system == null ? null : system.layout(text, style, box);
        if (layout == null) {
            throw new IllegalStateException("No font is loaded yet: the default font loads before Game.onStart");
        }
        return layout;
    }

    @Override
    public FontFamily defaultFont() {
        TextSystem system = textSystem;
        FontFamily family = system == null ? null : system.defaultFamily();
        if (family == null) {
            throw new IllegalStateException("The default font is not loaded yet; it is ready from Game.onStart");
        }
        return family;
    }

    @Override
    public Font gridFont(TextureRegion sheet, String characters, int cellWidth, int cellHeight) {
        return BitmapFontImpl.grid("grid", sheet, characters, cellWidth, cellHeight);
    }

    TextureImpl whiteTexture() {
        return white;
    }

    TextureImpl fallbackTexture() {
        return fallback;
    }

    /**
     * Returns an engine shader made of the default vertex shader and a fragment shader, compiled on first use.
     *
     * @param name a unique name
     * @param fragment the fragment source
     * @return the shader
     */
    ShaderImpl internalShader(String name, String fragment) {
        ShaderImpl shader = builtIn.get(name);
        if (shader == null) {
            shader = new ShaderImpl(gl, ShaderImpl.DEFAULT_VERTEX, fragment, includes, logger);
            builtIn.put(name, shader);
        }
        return shader;
    }

    /**
     * Returns the shader that draws light fans into a light map.
     *
     * @return the shader
     */
    public ShaderImpl lightShader() {
        return internalShader("gulp:light", PostShaders.LIGHT);
    }

    /**
     * Returns a soft round dot, white fading out to the edge; the default particle image.
     *
     * @return the region
     */
    public TextureRegion softDot() {
        TextureImpl dot = softDot;
        if (dot == null) {
            int size = 32;
            Pixmap pixels = new Pixmap(size, size);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float dx = (x + 0.5f) / size * 2f - 1f;
                    float dy = (y + 0.5f) / size * 2f - 1f;
                    float d = Math.min(1f, (float) Math.sqrt(dx * dx + dy * dy));
                    float a = smooth(1f - d);
                    pixels.setPixel(x, y, new Color(1f, 1f, 1f, a));
                }
            }
            dot = new TextureImpl(gl, size, size, TextureFilter.LINEAR, pixels);
            softDot = dot;
        }
        return dot.region();
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
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

    /**
     * Reads the pixels of a texture region back from the GPU, for example to make a cursor from an atlas sprite.
     *
     * @param region the region; rotation and trimming are ignored
     * @return the image
     */
    public Pixmap readRegion(TextureRegion region) {
        int width = region.width();
        int height = region.height();
        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
        if (region.texture() instanceof TextureImpl texture) {
            int framebuffer = gl.createFramebuffer();
            gl.bindFramebuffer(Gl.FRAMEBUFFER, framebuffer);
            gl.framebufferTexture2D(Gl.FRAMEBUFFER, Gl.COLOR_ATTACHMENT0, Gl.TEXTURE_2D, texture.handle(), 0);
            gl.pixelStorei(Gl.PACK_ALIGNMENT, 1);
            gl.readPixels(region.x(), region.y(), width, height, Gl.RGBA, Gl.UNSIGNED_BYTE, buffer);
            gl.bindFramebuffer(Gl.FRAMEBUFFER, 0);
            gl.deleteFramebuffer(framebuffer);
        }
        byte[] rgba = new byte[width * height * 4];
        buffer.get(rgba);
        return Pixmap.fromRgba(width, height, rgba);
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
        for (ShaderImpl shader : builtIn.values()) {
            if (shader != defaultShader) {
                shader.dispose();
            }
        }
        builtIn.clear();
        defaultShader.dispose();
        msdfShader.dispose();
        white.dispose();
        fallback.dispose();
        if (softDot != null) {
            softDot.dispose();
            softDot = null;
        }
    }
}
