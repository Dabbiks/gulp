package dev.gulp.platform;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Thin abstraction over OpenGL ES 3.0 / WebGL2, the common denominator of all backends. Object handles are plain
 * {@code int}s ({@code 0} means "none"), constants have the GLES values, and every method maps to exactly one GL call so
 * the engine controls state changes and allocations.
 *
 * <p>Desktop implements it with OpenGL 3.3 core, the web with WebGL2, headless as a no-op that counts calls.
 *
 * <pre>{@code
 * gl.viewport(0, 0, window.framebufferWidth(), window.framebufferHeight());
 * gl.clearColor(0.1f, 0.2f, 0.3f, 1f);
 * gl.clear(Gl.COLOR_BUFFER_BIT);
 * }</pre>
 *
 * <p>Buffers must be direct and in native byte order. Shaders are GLSL ES 3.00; the desktop backend rewrites the
 * {@code #version} header.
 */
public interface Gl {

    // ---- Constants: values are identical in GLES 3.0, WebGL2 and desktop GL 3.3 ----

    /** {@code GL_NO_ERROR}. */
    int NO_ERROR = 0;
    /** {@code GL_ZERO}. */
    int ZERO = 0;
    /** {@code GL_ONE}. */
    int ONE = 1;
    /** {@code GL_FALSE}. */
    int FALSE = 0;
    /** {@code GL_TRUE}. */
    int TRUE = 1;

    /** {@code GL_DEPTH_BUFFER_BIT}. */
    int DEPTH_BUFFER_BIT = 0x0100;
    /** {@code GL_STENCIL_BUFFER_BIT}. */
    int STENCIL_BUFFER_BIT = 0x0400;
    /** {@code GL_COLOR_BUFFER_BIT}. */
    int COLOR_BUFFER_BIT = 0x4000;

    /** {@code GL_POINTS}. */
    int POINTS = 0x0000;
    /** {@code GL_LINES}. */
    int LINES = 0x0001;
    /** {@code GL_LINE_STRIP}. */
    int LINE_STRIP = 0x0003;
    /** {@code GL_TRIANGLES}. */
    int TRIANGLES = 0x0004;
    /** {@code GL_TRIANGLE_STRIP}. */
    int TRIANGLE_STRIP = 0x0005;
    /** {@code GL_TRIANGLE_FAN}. */
    int TRIANGLE_FAN = 0x0006;

    /** {@code GL_CULL_FACE}. */
    int CULL_FACE = 0x0B44;
    /** {@code GL_DEPTH_TEST}. */
    int DEPTH_TEST = 0x0B71;
    /** {@code GL_STENCIL_TEST}. */
    int STENCIL_TEST = 0x0B90;
    /** {@code GL_BLEND}. */
    int BLEND = 0x0BE2;
    /** {@code GL_SCISSOR_TEST}. */
    int SCISSOR_TEST = 0x0C11;

    /** {@code GL_SRC_COLOR}. */
    int SRC_COLOR = 0x0300;
    /** {@code GL_ONE_MINUS_SRC_COLOR}. */
    int ONE_MINUS_SRC_COLOR = 0x0301;
    /** {@code GL_SRC_ALPHA}. */
    int SRC_ALPHA = 0x0302;
    /** {@code GL_ONE_MINUS_SRC_ALPHA}. */
    int ONE_MINUS_SRC_ALPHA = 0x0303;
    /** {@code GL_DST_ALPHA}. */
    int DST_ALPHA = 0x0304;
    /** {@code GL_ONE_MINUS_DST_ALPHA}. */
    int ONE_MINUS_DST_ALPHA = 0x0305;
    /** {@code GL_DST_COLOR}. */
    int DST_COLOR = 0x0306;
    /** {@code GL_ONE_MINUS_DST_COLOR}. */
    int ONE_MINUS_DST_COLOR = 0x0307;
    /** {@code GL_FUNC_ADD}. */
    int FUNC_ADD = 0x8006;
    /** {@code GL_MIN}. */
    int MIN = 0x8007;
    /** {@code GL_MAX}. */
    int MAX = 0x8008;
    /** {@code GL_FUNC_SUBTRACT}. */
    int FUNC_SUBTRACT = 0x800A;
    /** {@code GL_FUNC_REVERSE_SUBTRACT}. */
    int FUNC_REVERSE_SUBTRACT = 0x800B;

    /** {@code GL_BYTE}. */
    int BYTE = 0x1400;
    /** {@code GL_UNSIGNED_BYTE}. */
    int UNSIGNED_BYTE = 0x1401;
    /** {@code GL_SHORT}. */
    int SHORT = 0x1402;
    /** {@code GL_UNSIGNED_SHORT}. */
    int UNSIGNED_SHORT = 0x1403;
    /** {@code GL_INT}. */
    int INT = 0x1404;
    /** {@code GL_UNSIGNED_INT}. */
    int UNSIGNED_INT = 0x1405;
    /** {@code GL_FLOAT}. */
    int FLOAT = 0x1406;

    /** {@code GL_RED}. */
    int RED = 0x1903;
    /** {@code GL_RGB}. */
    int RGB = 0x1907;
    /** {@code GL_RGBA}. */
    int RGBA = 0x1908;
    /** {@code GL_RG}. */
    int RG = 0x8227;
    /** {@code GL_R8}. */
    int R8 = 0x8229;
    /** {@code GL_RG8}. */
    int RG8 = 0x822B;
    /** {@code GL_RGBA8}. */
    int RGBA8 = 0x8058;
    /** {@code GL_DEPTH24_STENCIL8}. */
    int DEPTH24_STENCIL8 = 0x88F0;

    /** {@code GL_VENDOR}. */
    int VENDOR = 0x1F00;
    /** {@code GL_RENDERER}. */
    int RENDERER = 0x1F01;
    /** {@code GL_VERSION}. */
    int VERSION = 0x1F02;
    /** {@code GL_UNPACK_ALIGNMENT}. */
    int UNPACK_ALIGNMENT = 0x0CF5;
    /** {@code GL_PACK_ALIGNMENT}. */
    int PACK_ALIGNMENT = 0x0D05;
    /** {@code GL_MAX_TEXTURE_SIZE}. */
    int MAX_TEXTURE_SIZE = 0x0D33;

    /** {@code GL_ARRAY_BUFFER}. */
    int ARRAY_BUFFER = 0x8892;
    /** {@code GL_ELEMENT_ARRAY_BUFFER}. */
    int ELEMENT_ARRAY_BUFFER = 0x8893;
    /** {@code GL_UNIFORM_BUFFER}. */
    int UNIFORM_BUFFER = 0x8A11;
    /** {@code GL_STREAM_DRAW}. */
    int STREAM_DRAW = 0x88E0;
    /** {@code GL_STATIC_DRAW}. */
    int STATIC_DRAW = 0x88E4;
    /** {@code GL_DYNAMIC_DRAW}. */
    int DYNAMIC_DRAW = 0x88E8;

    /** {@code GL_FRAGMENT_SHADER}. */
    int FRAGMENT_SHADER = 0x8B30;
    /** {@code GL_VERTEX_SHADER}. */
    int VERTEX_SHADER = 0x8B31;
    /** {@code GL_COMPILE_STATUS}. */
    int COMPILE_STATUS = 0x8B81;
    /** {@code GL_LINK_STATUS}. */
    int LINK_STATUS = 0x8B82;

    /** {@code GL_TEXTURE_2D}. */
    int TEXTURE_2D = 0x0DE1;
    /** {@code GL_TEXTURE0}; unit {@code n} is {@code TEXTURE0 + n}. */
    int TEXTURE0 = 0x84C0;
    /** {@code GL_TEXTURE_MAG_FILTER}. */
    int TEXTURE_MAG_FILTER = 0x2800;
    /** {@code GL_TEXTURE_MIN_FILTER}. */
    int TEXTURE_MIN_FILTER = 0x2801;
    /** {@code GL_TEXTURE_WRAP_S}. */
    int TEXTURE_WRAP_S = 0x2802;
    /** {@code GL_TEXTURE_WRAP_T}. */
    int TEXTURE_WRAP_T = 0x2803;
    /** {@code GL_NEAREST}. */
    int NEAREST = 0x2600;
    /** {@code GL_LINEAR}. */
    int LINEAR = 0x2601;
    /** {@code GL_NEAREST_MIPMAP_NEAREST}. */
    int NEAREST_MIPMAP_NEAREST = 0x2700;
    /** {@code GL_LINEAR_MIPMAP_NEAREST}. */
    int LINEAR_MIPMAP_NEAREST = 0x2701;
    /** {@code GL_NEAREST_MIPMAP_LINEAR}. */
    int NEAREST_MIPMAP_LINEAR = 0x2702;
    /** {@code GL_LINEAR_MIPMAP_LINEAR}. */
    int LINEAR_MIPMAP_LINEAR = 0x2703;
    /** {@code GL_REPEAT}. */
    int REPEAT = 0x2901;
    /** {@code GL_CLAMP_TO_EDGE}. */
    int CLAMP_TO_EDGE = 0x812F;
    /** {@code GL_MIRRORED_REPEAT}. */
    int MIRRORED_REPEAT = 0x8370;

    /** {@code GL_FRAMEBUFFER}. */
    int FRAMEBUFFER = 0x8D40;
    /** {@code GL_READ_FRAMEBUFFER}. */
    int READ_FRAMEBUFFER = 0x8CA8;
    /** {@code GL_DRAW_FRAMEBUFFER}. */
    int DRAW_FRAMEBUFFER = 0x8CA9;
    /** {@code GL_RENDERBUFFER}. */
    int RENDERBUFFER = 0x8D41;
    /** {@code GL_COLOR_ATTACHMENT0}. */
    int COLOR_ATTACHMENT0 = 0x8CE0;
    /** {@code GL_DEPTH_STENCIL_ATTACHMENT}. */
    int DEPTH_STENCIL_ATTACHMENT = 0x821A;
    /** {@code GL_FRAMEBUFFER_COMPLETE}. */
    int FRAMEBUFFER_COMPLETE = 0x8CD5;

    // ---- State ----

    /**
     * {@code glGetError}.
     *
     * @return the oldest recorded error, or {@link #NO_ERROR}
     */
    int getError();

    /**
     * {@code glGetIntegerv} for a single value.
     *
     * @param pname the parameter, for example {@link #MAX_TEXTURE_SIZE}
     * @return its value
     */
    int getInteger(int pname);

    /**
     * {@code glGetString}.
     *
     * @param name {@link #VENDOR}, {@link #RENDERER} or {@link #VERSION}
     * @return the string, or {@code null} if unavailable
     */
    @Nullable String getString(int name);

    /**
     * {@code glEnable}.
     *
     * @param capability for example {@link #BLEND}
     */
    void enable(int capability);

    /**
     * {@code glDisable}.
     *
     * @param capability for example {@link #SCISSOR_TEST}
     */
    void disable(int capability);

    /**
     * {@code glViewport}.
     *
     * @param x left edge in framebuffer pixels
     * @param y bottom edge in framebuffer pixels
     * @param width width in pixels
     * @param height height in pixels
     */
    void viewport(int x, int y, int width, int height);

    /**
     * {@code glScissor}.
     *
     * @param x left edge in framebuffer pixels
     * @param y bottom edge in framebuffer pixels
     * @param width width in pixels
     * @param height height in pixels
     */
    void scissor(int x, int y, int width, int height);

    /**
     * {@code glClearColor}.
     *
     * @param r red, {@code 0..1}
     * @param g green, {@code 0..1}
     * @param b blue, {@code 0..1}
     * @param a alpha, {@code 0..1}
     */
    void clearColor(float r, float g, float b, float a);

    /**
     * {@code glClear}.
     *
     * @param mask a combination of {@link #COLOR_BUFFER_BIT}, {@link #DEPTH_BUFFER_BIT}, {@link #STENCIL_BUFFER_BIT}
     */
    void clear(int mask);

    /**
     * {@code glColorMask}.
     *
     * @param r write red
     * @param g write green
     * @param b write blue
     * @param a write alpha
     */
    void colorMask(boolean r, boolean g, boolean b, boolean a);

    /**
     * {@code glBlendFunc}.
     *
     * @param sourceFactor source factor
     * @param destinationFactor destination factor
     */
    void blendFunc(int sourceFactor, int destinationFactor);

    /**
     * {@code glBlendFuncSeparate}.
     *
     * @param sourceRgb source RGB factor
     * @param destinationRgb destination RGB factor
     * @param sourceAlpha source alpha factor
     * @param destinationAlpha destination alpha factor
     */
    void blendFuncSeparate(int sourceRgb, int destinationRgb, int sourceAlpha, int destinationAlpha);

    /**
     * {@code glBlendEquation}.
     *
     * @param mode for example {@link #FUNC_ADD}
     */
    void blendEquation(int mode);

    /**
     * {@code glBlendEquationSeparate}.
     *
     * @param modeRgb equation for RGB
     * @param modeAlpha equation for alpha
     */
    void blendEquationSeparate(int modeRgb, int modeAlpha);

    /**
     * {@code glPixelStorei}.
     *
     * @param pname {@link #UNPACK_ALIGNMENT} or {@link #PACK_ALIGNMENT}
     * @param value 1, 2, 4 or 8
     */
    void pixelStorei(int pname, int value);

    // ---- Buffers and vertex arrays ----

    /**
     * {@code glGenBuffers} for one buffer.
     *
     * @return the new buffer handle
     */
    int createBuffer();

    /**
     * {@code glDeleteBuffers} for one buffer.
     *
     * @param buffer the handle; {@code 0} is ignored
     */
    void deleteBuffer(int buffer);

    /**
     * {@code glBindBuffer}.
     *
     * @param target for example {@link #ARRAY_BUFFER}
     * @param buffer the handle, or {@code 0} to unbind
     */
    void bindBuffer(int target, int buffer);

    /**
     * {@code glBufferData} with contents.
     *
     * @param target the bound target
     * @param data bytes from position to limit are uploaded
     * @param usage for example {@link #DYNAMIC_DRAW}
     */
    void bufferData(int target, ByteBuffer data, int usage);

    /**
     * {@code glBufferData} allocating uninitialised storage.
     *
     * @param target the bound target
     * @param sizeInBytes storage size
     * @param usage for example {@link #STREAM_DRAW}
     */
    void bufferData(int target, int sizeInBytes, int usage);

    /**
     * {@code glBufferSubData}.
     *
     * @param target the bound target
     * @param offsetInBytes where to write in the buffer
     * @param data bytes from position to limit are uploaded
     */
    void bufferSubData(int target, int offsetInBytes, ByteBuffer data);

    /**
     * {@code glBindBufferBase}.
     *
     * @param target {@link #UNIFORM_BUFFER}
     * @param index binding point
     * @param buffer the handle
     */
    void bindBufferBase(int target, int index, int buffer);

    /**
     * {@code glGenVertexArrays} for one array.
     *
     * @return the new vertex array handle
     */
    int createVertexArray();

    /**
     * {@code glDeleteVertexArrays} for one array.
     *
     * @param vertexArray the handle; {@code 0} is ignored
     */
    void deleteVertexArray(int vertexArray);

    /**
     * {@code glBindVertexArray}.
     *
     * @param vertexArray the handle, or {@code 0} to unbind
     */
    void bindVertexArray(int vertexArray);

    /**
     * {@code glEnableVertexAttribArray}.
     *
     * @param index attribute location
     */
    void enableVertexAttribArray(int index);

    /**
     * {@code glDisableVertexAttribArray}.
     *
     * @param index attribute location
     */
    void disableVertexAttribArray(int index);

    /**
     * {@code glVertexAttribPointer} reading from the bound {@link #ARRAY_BUFFER}.
     *
     * @param index attribute location
     * @param size components per vertex, 1 to 4
     * @param type for example {@link #FLOAT} or {@link #UNSIGNED_BYTE}
     * @param normalized whether integer values map to {@code 0..1} or {@code -1..1}
     * @param strideInBytes distance between vertices
     * @param offsetInBytes offset of the first component
     */
    void vertexAttribPointer(int index, int size, int type, boolean normalized, int strideInBytes, int offsetInBytes);

    /**
     * {@code glVertexAttribDivisor}.
     *
     * @param index attribute location
     * @param divisor {@code 0} per vertex, {@code 1} per instance
     */
    void vertexAttribDivisor(int index, int divisor);

    // ---- Shaders and programs ----

    /**
     * {@code glCreateShader}.
     *
     * @param type {@link #VERTEX_SHADER} or {@link #FRAGMENT_SHADER}
     * @return the new shader handle
     */
    int createShader(int type);

    /**
     * {@code glShaderSource}.
     *
     * @param shader the handle
     * @param source GLSL ES 3.00 source
     */
    void shaderSource(int shader, String source);

    /**
     * {@code glCompileShader}.
     *
     * @param shader the handle
     */
    void compileShader(int shader);

    /**
     * {@code glGetShaderiv}.
     *
     * @param shader the handle
     * @param pname for example {@link #COMPILE_STATUS}
     * @return the value
     */
    int getShaderi(int shader, int pname);

    /**
     * {@code glGetShaderInfoLog}.
     *
     * @param shader the handle
     * @return the log, empty if there is none
     */
    String getShaderInfoLog(int shader);

    /**
     * {@code glDeleteShader}.
     *
     * @param shader the handle; {@code 0} is ignored
     */
    void deleteShader(int shader);

    /**
     * {@code glCreateProgram}.
     *
     * @return the new program handle
     */
    int createProgram();

    /**
     * {@code glAttachShader}.
     *
     * @param program the program
     * @param shader the shader
     */
    void attachShader(int program, int shader);

    /**
     * {@code glDetachShader}.
     *
     * @param program the program
     * @param shader the shader
     */
    void detachShader(int program, int shader);

    /**
     * {@code glBindAttribLocation}; must be called before {@link #linkProgram(int)}.
     *
     * @param program the program
     * @param index the location to assign
     * @param name the attribute name
     */
    void bindAttribLocation(int program, int index, String name);

    /**
     * {@code glLinkProgram}.
     *
     * @param program the program
     */
    void linkProgram(int program);

    /**
     * {@code glGetProgramiv}.
     *
     * @param program the program
     * @param pname for example {@link #LINK_STATUS}
     * @return the value
     */
    int getProgrami(int program, int pname);

    /**
     * {@code glGetProgramInfoLog}.
     *
     * @param program the program
     * @return the log, empty if there is none
     */
    String getProgramInfoLog(int program);

    /**
     * {@code glUseProgram}.
     *
     * @param program the program, or {@code 0} for none
     */
    void useProgram(int program);

    /**
     * {@code glDeleteProgram}.
     *
     * @param program the program; {@code 0} is ignored
     */
    void deleteProgram(int program);

    /**
     * {@code glGetUniformLocation}. Look locations up once after linking, not every frame.
     *
     * @param program the program
     * @param name the uniform name
     * @return the location, or {@code -1} if the uniform is not active
     */
    int getUniformLocation(int program, String name);

    /**
     * {@code glGetUniformBlockIndex}.
     *
     * @param program the program
     * @param name the block name
     * @return the block index
     */
    int getUniformBlockIndex(int program, String name);

    /**
     * {@code glUniformBlockBinding}.
     *
     * @param program the program
     * @param blockIndex the block index
     * @param binding the binding point
     */
    void uniformBlockBinding(int program, int blockIndex, int binding);

    /**
     * {@code glUniform1i}.
     *
     * @param location the location
     * @param x value, also used for sampler units
     */
    void uniform1i(int location, int x);

    /**
     * {@code glUniform1f}.
     *
     * @param location the location
     * @param x value
     */
    void uniform1f(int location, float x);

    /**
     * {@code glUniform2f}.
     *
     * @param location the location
     * @param x first component
     * @param y second component
     */
    void uniform2f(int location, float x, float y);

    /**
     * {@code glUniform3f}.
     *
     * @param location the location
     * @param x first component
     * @param y second component
     * @param z third component
     */
    void uniform3f(int location, float x, float y, float z);

    /**
     * {@code glUniform4f}.
     *
     * @param location the location
     * @param x first component
     * @param y second component
     * @param z third component
     * @param w fourth component
     */
    void uniform4f(int location, float x, float y, float z, float w);

    /**
     * {@code glUniform1fv}.
     *
     * @param location the location
     * @param values floats from position to limit
     */
    void uniform1fv(int location, FloatBuffer values);

    /**
     * {@code glUniform4fv}.
     *
     * @param location the location
     * @param values floats from position to limit, a multiple of 4
     */
    void uniform4fv(int location, FloatBuffer values);

    /**
     * {@code glUniformMatrix3fv} without transposition.
     *
     * @param location the location
     * @param values column-major 3x3 matrices from position to limit
     */
    void uniformMatrix3fv(int location, FloatBuffer values);

    /**
     * {@code glUniformMatrix4fv} without transposition.
     *
     * @param location the location
     * @param values column-major 4x4 matrices from position to limit
     */
    void uniformMatrix4fv(int location, FloatBuffer values);

    // ---- Textures ----

    /**
     * {@code glGenTextures} for one texture.
     *
     * @return the new texture handle
     */
    int createTexture();

    /**
     * {@code glDeleteTextures} for one texture.
     *
     * @param texture the handle; {@code 0} is ignored
     */
    void deleteTexture(int texture);

    /**
     * {@code glActiveTexture}.
     *
     * @param unit {@link #TEXTURE0} plus the unit index
     */
    void activeTexture(int unit);

    /**
     * {@code glBindTexture}.
     *
     * @param target {@link #TEXTURE_2D}
     * @param texture the handle, or {@code 0} to unbind
     */
    void bindTexture(int target, int texture);

    /**
     * {@code glTexParameteri}.
     *
     * @param target {@link #TEXTURE_2D}
     * @param pname for example {@link #TEXTURE_MIN_FILTER}
     * @param value for example {@link #NEAREST}
     */
    void texParameteri(int target, int pname, int value);

    /**
     * {@code glTexImage2D}.
     *
     * @param target {@link #TEXTURE_2D}
     * @param level mipmap level
     * @param internalFormat for example {@link #RGBA8}
     * @param width width in pixels
     * @param height height in pixels
     * @param format for example {@link #RGBA}
     * @param type for example {@link #UNSIGNED_BYTE}
     * @param pixels pixel data from position, or {@code null} to only allocate
     */
    void texImage2D(
            int target,
            int level,
            int internalFormat,
            int width,
            int height,
            int format,
            int type,
            @Nullable ByteBuffer pixels);

    /**
     * {@code glTexSubImage2D}.
     *
     * @param target {@link #TEXTURE_2D}
     * @param level mipmap level
     * @param x left edge of the updated region
     * @param y top edge of the updated region
     * @param width region width
     * @param height region height
     * @param format for example {@link #RGBA}
     * @param type for example {@link #UNSIGNED_BYTE}
     * @param pixels pixel data from position
     */
    void texSubImage2D(
            int target, int level, int x, int y, int width, int height, int format, int type, ByteBuffer pixels);

    /**
     * {@code glGenerateMipmap}.
     *
     * @param target {@link #TEXTURE_2D}
     */
    void generateMipmap(int target);

    // ---- Framebuffers ----

    /**
     * {@code glGenFramebuffers} for one framebuffer.
     *
     * @return the new framebuffer handle
     */
    int createFramebuffer();

    /**
     * {@code glDeleteFramebuffers} for one framebuffer.
     *
     * @param framebuffer the handle; {@code 0} is ignored
     */
    void deleteFramebuffer(int framebuffer);

    /**
     * {@code glBindFramebuffer}.
     *
     * @param target {@link #FRAMEBUFFER}, {@link #READ_FRAMEBUFFER} or {@link #DRAW_FRAMEBUFFER}
     * @param framebuffer the handle, or {@code 0} for the window
     */
    void bindFramebuffer(int target, int framebuffer);

    /**
     * {@code glFramebufferTexture2D}.
     *
     * @param target the bound framebuffer target
     * @param attachment for example {@link #COLOR_ATTACHMENT0}
     * @param textureTarget {@link #TEXTURE_2D}
     * @param texture the texture handle
     * @param level mipmap level
     */
    void framebufferTexture2D(int target, int attachment, int textureTarget, int texture, int level);

    /**
     * {@code glCheckFramebufferStatus}.
     *
     * @param target the bound framebuffer target
     * @return {@link #FRAMEBUFFER_COMPLETE} or an error status
     */
    int checkFramebufferStatus(int target);

    /**
     * {@code glGenRenderbuffers} for one renderbuffer.
     *
     * @return the new renderbuffer handle
     */
    int createRenderbuffer();

    /**
     * {@code glDeleteRenderbuffers} for one renderbuffer.
     *
     * @param renderbuffer the handle; {@code 0} is ignored
     */
    void deleteRenderbuffer(int renderbuffer);

    /**
     * {@code glBindRenderbuffer}.
     *
     * @param target {@link #RENDERBUFFER}
     * @param renderbuffer the handle, or {@code 0} to unbind
     */
    void bindRenderbuffer(int target, int renderbuffer);

    /**
     * {@code glRenderbufferStorage}.
     *
     * @param target {@link #RENDERBUFFER}
     * @param internalFormat for example {@link #DEPTH24_STENCIL8}
     * @param width width in pixels
     * @param height height in pixels
     */
    void renderbufferStorage(int target, int internalFormat, int width, int height);

    /**
     * {@code glFramebufferRenderbuffer}.
     *
     * @param target the bound framebuffer target
     * @param attachment for example {@link #DEPTH_STENCIL_ATTACHMENT}
     * @param renderbufferTarget {@link #RENDERBUFFER}
     * @param renderbuffer the renderbuffer handle
     */
    void framebufferRenderbuffer(int target, int attachment, int renderbufferTarget, int renderbuffer);

    /**
     * {@code glBlitFramebuffer} of the color buffer.
     *
     * @param srcX0 source left
     * @param srcY0 source bottom
     * @param srcX1 source right
     * @param srcY1 source top
     * @param dstX0 destination left
     * @param dstY0 destination bottom
     * @param dstX1 destination right
     * @param dstY1 destination top
     * @param filter {@link #NEAREST} or {@link #LINEAR}
     */
    void blitFramebuffer(
            int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int filter);

    /**
     * {@code glReadPixels}.
     *
     * @param x left edge
     * @param y bottom edge
     * @param width width in pixels
     * @param height height in pixels
     * @param format {@link #RGBA}
     * @param type {@link #UNSIGNED_BYTE}
     * @param pixels receives the pixels from its position
     */
    void readPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels);

    // ---- Drawing ----

    /**
     * {@code glDrawArrays}.
     *
     * @param mode for example {@link #TRIANGLES}
     * @param first first vertex
     * @param count number of vertices
     */
    void drawArrays(int mode, int first, int count);

    /**
     * {@code glDrawElements} reading indices from the bound {@link #ELEMENT_ARRAY_BUFFER}.
     *
     * @param mode for example {@link #TRIANGLES}
     * @param count number of indices
     * @param type {@link #UNSIGNED_SHORT} or {@link #UNSIGNED_INT}
     * @param offsetInBytes offset into the index buffer
     */
    void drawElements(int mode, int count, int type, int offsetInBytes);

    /**
     * {@code glDrawArraysInstanced}.
     *
     * @param mode for example {@link #TRIANGLES}
     * @param first first vertex
     * @param count vertices per instance
     * @param instanceCount number of instances
     */
    void drawArraysInstanced(int mode, int first, int count, int instanceCount);

    /**
     * {@code glDrawElementsInstanced}.
     *
     * @param mode for example {@link #TRIANGLES}
     * @param count indices per instance
     * @param type {@link #UNSIGNED_SHORT} or {@link #UNSIGNED_INT}
     * @param offsetInBytes offset into the index buffer
     * @param instanceCount number of instances
     */
    void drawElementsInstanced(int mode, int count, int type, int offsetInBytes, int instanceCount);
}
