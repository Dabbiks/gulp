package dev.gulp.backend.headless;

import dev.gulp.platform.Gl;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import org.jspecify.annotations.Nullable;

/**
 * No-op graphics that counts calls. Handles are unique positive integers, shaders always compile and programs always
 * link, framebuffers are always complete.
 *
 * <pre>{@code
 * assertThat(backend.gl().clearCount()).isEqualTo(60);
 * assertThat(backend.gl().lastClearColor()).containsExactly(0.1f, 0.2f, 0.3f, 1f);
 * }</pre>
 */
public final class HeadlessGl implements Gl {

    private final float[] lastClearColor = new float[4];
    private final int[] viewport = new int[4];
    private long callCount;
    private long clearCount;
    private long drawCallCount;
    private int nextHandle = 1;

    HeadlessGl() {}

    /**
     * Returns the total number of GL calls.
     *
     * @return all calls since creation
     */
    public long callCount() {
        return callCount;
    }

    /**
     * Returns the number of {@link #clear(int)} calls.
     *
     * @return clears since creation
     */
    public long clearCount() {
        return clearCount;
    }

    /**
     * Returns the number of draw calls of any kind.
     *
     * @return draw calls since creation
     */
    public long drawCallCount() {
        return drawCallCount;
    }

    /**
     * Returns the last color passed to {@link #clearColor(float, float, float, float)}.
     *
     * @return a copy as {@code {r, g, b, a}}
     */
    public float[] lastClearColor() {
        return lastClearColor.clone();
    }

    /**
     * Returns the last viewport.
     *
     * @return a copy as {@code {x, y, width, height}}
     */
    public int[] viewport() {
        return viewport.clone();
    }

    private void call() {
        callCount++;
    }

    private int handle() {
        call();
        return nextHandle++;
    }

    private void draw() {
        call();
        drawCallCount++;
    }

    @Override
    public int getError() {
        call();
        return NO_ERROR;
    }

    @Override
    public int getInteger(int pname) {
        call();
        return pname == MAX_TEXTURE_SIZE ? 8192 : 0;
    }

    @Override
    public @Nullable String getString(int name) {
        call();
        return switch (name) {
            case VENDOR -> "Gulp";
            case RENDERER -> "Headless";
            case VERSION -> "OpenGL ES 3.0 (headless)";
            default -> null;
        };
    }

    @Override
    public void enable(int capability) {
        call();
    }

    @Override
    public void disable(int capability) {
        call();
    }

    @Override
    public void viewport(int x, int y, int width, int height) {
        call();
        viewport[0] = x;
        viewport[1] = y;
        viewport[2] = width;
        viewport[3] = height;
    }

    @Override
    public void scissor(int x, int y, int width, int height) {
        call();
    }

    @Override
    public void clearColor(float r, float g, float b, float a) {
        call();
        lastClearColor[0] = r;
        lastClearColor[1] = g;
        lastClearColor[2] = b;
        lastClearColor[3] = a;
    }

    @Override
    public void clear(int mask) {
        call();
        clearCount++;
    }

    @Override
    public void colorMask(boolean r, boolean g, boolean b, boolean a) {
        call();
    }

    @Override
    public void blendFunc(int sourceFactor, int destinationFactor) {
        call();
    }

    @Override
    public void blendFuncSeparate(int sourceRgb, int destinationRgb, int sourceAlpha, int destinationAlpha) {
        call();
    }

    @Override
    public void blendEquation(int mode) {
        call();
    }

    @Override
    public void blendEquationSeparate(int modeRgb, int modeAlpha) {
        call();
    }

    @Override
    public void pixelStorei(int pname, int value) {
        call();
    }

    @Override
    public int createBuffer() {
        return handle();
    }

    @Override
    public void deleteBuffer(int buffer) {
        call();
    }

    @Override
    public void bindBuffer(int target, int buffer) {
        call();
    }

    @Override
    public void bufferData(int target, ByteBuffer data, int usage) {
        call();
    }

    @Override
    public void bufferData(int target, int sizeInBytes, int usage) {
        call();
    }

    @Override
    public void bufferSubData(int target, int offsetInBytes, ByteBuffer data) {
        call();
    }

    @Override
    public void bindBufferBase(int target, int index, int buffer) {
        call();
    }

    @Override
    public int createVertexArray() {
        return handle();
    }

    @Override
    public void deleteVertexArray(int vertexArray) {
        call();
    }

    @Override
    public void bindVertexArray(int vertexArray) {
        call();
    }

    @Override
    public void enableVertexAttribArray(int index) {
        call();
    }

    @Override
    public void disableVertexAttribArray(int index) {
        call();
    }

    @Override
    public void vertexAttribPointer(
            int index, int size, int type, boolean normalized, int strideInBytes, int offsetInBytes) {
        call();
    }

    @Override
    public void vertexAttribDivisor(int index, int divisor) {
        call();
    }

    @Override
    public int createShader(int type) {
        return handle();
    }

    @Override
    public void shaderSource(int shader, String source) {
        call();
    }

    @Override
    public void compileShader(int shader) {
        call();
    }

    @Override
    public int getShaderi(int shader, int pname) {
        call();
        return pname == COMPILE_STATUS ? TRUE : 0;
    }

    @Override
    public String getShaderInfoLog(int shader) {
        call();
        return "";
    }

    @Override
    public void deleteShader(int shader) {
        call();
    }

    @Override
    public int createProgram() {
        return handle();
    }

    @Override
    public void attachShader(int program, int shader) {
        call();
    }

    @Override
    public void detachShader(int program, int shader) {
        call();
    }

    @Override
    public void bindAttribLocation(int program, int index, String name) {
        call();
    }

    @Override
    public void linkProgram(int program) {
        call();
    }

    @Override
    public int getProgrami(int program, int pname) {
        call();
        return pname == LINK_STATUS ? TRUE : 0;
    }

    @Override
    public String getProgramInfoLog(int program) {
        call();
        return "";
    }

    @Override
    public void useProgram(int program) {
        call();
    }

    @Override
    public void deleteProgram(int program) {
        call();
    }

    @Override
    public int getUniformLocation(int program, String name) {
        return handle();
    }

    @Override
    public int getUniformBlockIndex(int program, String name) {
        return handle();
    }

    @Override
    public void uniformBlockBinding(int program, int blockIndex, int binding) {
        call();
    }

    @Override
    public void uniform1i(int location, int x) {
        call();
    }

    @Override
    public void uniform1f(int location, float x) {
        call();
    }

    @Override
    public void uniform2f(int location, float x, float y) {
        call();
    }

    @Override
    public void uniform3f(int location, float x, float y, float z) {
        call();
    }

    @Override
    public void uniform4f(int location, float x, float y, float z, float w) {
        call();
    }

    @Override
    public void uniform1fv(int location, FloatBuffer values) {
        call();
    }

    @Override
    public void uniform4fv(int location, FloatBuffer values) {
        call();
    }

    @Override
    public void uniformMatrix3fv(int location, FloatBuffer values) {
        call();
    }

    @Override
    public void uniformMatrix4fv(int location, FloatBuffer values) {
        call();
    }

    @Override
    public int createTexture() {
        return handle();
    }

    @Override
    public void deleteTexture(int texture) {
        call();
    }

    @Override
    public void activeTexture(int unit) {
        call();
    }

    @Override
    public void bindTexture(int target, int texture) {
        call();
    }

    @Override
    public void texParameteri(int target, int pname, int value) {
        call();
    }

    @Override
    public void texImage2D(
            int target,
            int level,
            int internalFormat,
            int width,
            int height,
            int format,
            int type,
            @Nullable ByteBuffer pixels) {
        call();
    }

    @Override
    public void texSubImage2D(
            int target, int level, int x, int y, int width, int height, int format, int type, ByteBuffer pixels) {
        call();
    }

    @Override
    public void generateMipmap(int target) {
        call();
    }

    @Override
    public int createFramebuffer() {
        return handle();
    }

    @Override
    public void deleteFramebuffer(int framebuffer) {
        call();
    }

    @Override
    public void bindFramebuffer(int target, int framebuffer) {
        call();
    }

    @Override
    public void framebufferTexture2D(int target, int attachment, int textureTarget, int texture, int level) {
        call();
    }

    @Override
    public int checkFramebufferStatus(int target) {
        call();
        return FRAMEBUFFER_COMPLETE;
    }

    @Override
    public int createRenderbuffer() {
        return handle();
    }

    @Override
    public void deleteRenderbuffer(int renderbuffer) {
        call();
    }

    @Override
    public void bindRenderbuffer(int target, int renderbuffer) {
        call();
    }

    @Override
    public void renderbufferStorage(int target, int internalFormat, int width, int height) {
        call();
    }

    @Override
    public void framebufferRenderbuffer(int target, int attachment, int renderbufferTarget, int renderbuffer) {
        call();
    }

    @Override
    public void blitFramebuffer(
            int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int filter) {
        call();
    }

    @Override
    public void readPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels) {
        call();
    }

    @Override
    public void drawArrays(int mode, int first, int count) {
        draw();
    }

    @Override
    public void drawElements(int mode, int count, int type, int offsetInBytes) {
        draw();
    }

    @Override
    public void drawArraysInstanced(int mode, int first, int count, int instanceCount) {
        draw();
    }

    @Override
    public void drawElementsInstanced(int mode, int count, int type, int offsetInBytes, int instanceCount) {
        draw();
    }
}
