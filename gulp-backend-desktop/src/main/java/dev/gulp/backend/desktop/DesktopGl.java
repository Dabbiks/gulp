package dev.gulp.backend.desktop;

import dev.gulp.platform.Gl;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL33C;

/**
 * {@link Gl} on desktop OpenGL 3.3 core through LWJGL. Each method is a direct call; shader sources are translated
 * from GLSL ES 3.00 to GLSL 3.30 core.
 *
 * <pre>{@code
 * backend.gl().clear(Gl.COLOR_BUFFER_BIT);
 * }</pre>
 */
public final class DesktopGl implements Gl {

    DesktopGl() {}

    @Override
    public int getError() {
        return GL33C.glGetError();
    }

    @Override
    public int getInteger(int pname) {
        return GL33C.glGetInteger(pname);
    }

    @Override
    public @Nullable String getString(int name) {
        return GL33C.glGetString(name);
    }

    @Override
    public void enable(int capability) {
        GL33C.glEnable(capability);
    }

    @Override
    public void disable(int capability) {
        GL33C.glDisable(capability);
    }

    @Override
    public void viewport(int x, int y, int width, int height) {
        GL33C.glViewport(x, y, width, height);
    }

    @Override
    public void scissor(int x, int y, int width, int height) {
        GL33C.glScissor(x, y, width, height);
    }

    @Override
    public void clearColor(float r, float g, float b, float a) {
        GL33C.glClearColor(r, g, b, a);
    }

    @Override
    public void clear(int mask) {
        GL33C.glClear(mask);
    }

    @Override
    public void colorMask(boolean r, boolean g, boolean b, boolean a) {
        GL33C.glColorMask(r, g, b, a);
    }

    @Override
    public void blendFunc(int sourceFactor, int destinationFactor) {
        GL33C.glBlendFunc(sourceFactor, destinationFactor);
    }

    @Override
    public void blendFuncSeparate(int sourceRgb, int destinationRgb, int sourceAlpha, int destinationAlpha) {
        GL33C.glBlendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
    }

    @Override
    public void blendEquation(int mode) {
        GL33C.glBlendEquation(mode);
    }

    @Override
    public void blendEquationSeparate(int modeRgb, int modeAlpha) {
        GL33C.glBlendEquationSeparate(modeRgb, modeAlpha);
    }

    @Override
    public void pixelStorei(int pname, int value) {
        GL33C.glPixelStorei(pname, value);
    }

    @Override
    public int createBuffer() {
        return GL33C.glGenBuffers();
    }

    @Override
    public void deleteBuffer(int buffer) {
        GL33C.glDeleteBuffers(buffer);
    }

    @Override
    public void bindBuffer(int target, int buffer) {
        GL33C.glBindBuffer(target, buffer);
    }

    @Override
    public void bufferData(int target, ByteBuffer data, int usage) {
        GL33C.glBufferData(target, data, usage);
    }

    @Override
    public void bufferData(int target, int sizeInBytes, int usage) {
        GL33C.glBufferData(target, sizeInBytes, usage);
    }

    @Override
    public void bufferSubData(int target, int offsetInBytes, ByteBuffer data) {
        GL33C.glBufferSubData(target, offsetInBytes, data);
    }

    @Override
    public void bindBufferBase(int target, int index, int buffer) {
        GL33C.glBindBufferBase(target, index, buffer);
    }

    @Override
    public int createVertexArray() {
        return GL33C.glGenVertexArrays();
    }

    @Override
    public void deleteVertexArray(int vertexArray) {
        GL33C.glDeleteVertexArrays(vertexArray);
    }

    @Override
    public void bindVertexArray(int vertexArray) {
        GL33C.glBindVertexArray(vertexArray);
    }

    @Override
    public void enableVertexAttribArray(int index) {
        GL33C.glEnableVertexAttribArray(index);
    }

    @Override
    public void disableVertexAttribArray(int index) {
        GL33C.glDisableVertexAttribArray(index);
    }

    @Override
    public void vertexAttribPointer(
            int index, int size, int type, boolean normalized, int strideInBytes, int offsetInBytes) {
        GL33C.glVertexAttribPointer(index, size, type, normalized, strideInBytes, offsetInBytes);
    }

    @Override
    public void vertexAttribDivisor(int index, int divisor) {
        GL33C.glVertexAttribDivisor(index, divisor);
    }

    @Override
    public int createShader(int type) {
        return GL33C.glCreateShader(type);
    }

    @Override
    public void shaderSource(int shader, String source) {
        GL33C.glShaderSource(shader, ShaderSources.toDesktop(source));
    }

    @Override
    public void compileShader(int shader) {
        GL33C.glCompileShader(shader);
    }

    @Override
    public int getShaderi(int shader, int pname) {
        return GL33C.glGetShaderi(shader, pname);
    }

    @Override
    public String getShaderInfoLog(int shader) {
        return GL33C.glGetShaderInfoLog(shader);
    }

    @Override
    public void deleteShader(int shader) {
        GL33C.glDeleteShader(shader);
    }

    @Override
    public int createProgram() {
        return GL33C.glCreateProgram();
    }

    @Override
    public void attachShader(int program, int shader) {
        GL33C.glAttachShader(program, shader);
    }

    @Override
    public void detachShader(int program, int shader) {
        GL33C.glDetachShader(program, shader);
    }

    @Override
    public void bindAttribLocation(int program, int index, String name) {
        GL33C.glBindAttribLocation(program, index, name);
    }

    @Override
    public void linkProgram(int program) {
        GL33C.glLinkProgram(program);
    }

    @Override
    public int getProgrami(int program, int pname) {
        return GL33C.glGetProgrami(program, pname);
    }

    @Override
    public String getProgramInfoLog(int program) {
        return GL33C.glGetProgramInfoLog(program);
    }

    @Override
    public void useProgram(int program) {
        GL33C.glUseProgram(program);
    }

    @Override
    public void deleteProgram(int program) {
        GL33C.glDeleteProgram(program);
    }

    @Override
    public int getUniformLocation(int program, String name) {
        return GL33C.glGetUniformLocation(program, name);
    }

    @Override
    public int getUniformBlockIndex(int program, String name) {
        return GL33C.glGetUniformBlockIndex(program, name);
    }

    @Override
    public void uniformBlockBinding(int program, int blockIndex, int binding) {
        GL33C.glUniformBlockBinding(program, blockIndex, binding);
    }

    @Override
    public void uniform1i(int location, int x) {
        GL33C.glUniform1i(location, x);
    }

    @Override
    public void uniform1f(int location, float x) {
        GL33C.glUniform1f(location, x);
    }

    @Override
    public void uniform2f(int location, float x, float y) {
        GL33C.glUniform2f(location, x, y);
    }

    @Override
    public void uniform3f(int location, float x, float y, float z) {
        GL33C.glUniform3f(location, x, y, z);
    }

    @Override
    public void uniform4f(int location, float x, float y, float z, float w) {
        GL33C.glUniform4f(location, x, y, z, w);
    }

    @Override
    public void uniform1fv(int location, FloatBuffer values) {
        GL33C.glUniform1fv(location, values);
    }

    @Override
    public void uniform4fv(int location, FloatBuffer values) {
        GL33C.glUniform4fv(location, values);
    }

    @Override
    public void uniformMatrix3fv(int location, FloatBuffer values) {
        GL33C.glUniformMatrix3fv(location, false, values);
    }

    @Override
    public void uniformMatrix4fv(int location, FloatBuffer values) {
        GL33C.glUniformMatrix4fv(location, false, values);
    }

    @Override
    public int createTexture() {
        return GL33C.glGenTextures();
    }

    @Override
    public void deleteTexture(int texture) {
        GL33C.glDeleteTextures(texture);
    }

    @Override
    public void activeTexture(int unit) {
        GL33C.glActiveTexture(unit);
    }

    @Override
    public void bindTexture(int target, int texture) {
        GL33C.glBindTexture(target, texture);
    }

    @Override
    public void texParameteri(int target, int pname, int value) {
        GL33C.glTexParameteri(target, pname, value);
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
        GL33C.glTexImage2D(target, level, internalFormat, width, height, 0, format, type, pixels);
    }

    @Override
    public void texSubImage2D(
            int target, int level, int x, int y, int width, int height, int format, int type, ByteBuffer pixels) {
        GL33C.glTexSubImage2D(target, level, x, y, width, height, format, type, pixels);
    }

    @Override
    public void generateMipmap(int target) {
        GL33C.glGenerateMipmap(target);
    }

    @Override
    public int createFramebuffer() {
        return GL33C.glGenFramebuffers();
    }

    @Override
    public void deleteFramebuffer(int framebuffer) {
        GL33C.glDeleteFramebuffers(framebuffer);
    }

    @Override
    public void bindFramebuffer(int target, int framebuffer) {
        GL33C.glBindFramebuffer(target, framebuffer);
    }

    @Override
    public void framebufferTexture2D(int target, int attachment, int textureTarget, int texture, int level) {
        GL33C.glFramebufferTexture2D(target, attachment, textureTarget, texture, level);
    }

    @Override
    public int checkFramebufferStatus(int target) {
        return GL33C.glCheckFramebufferStatus(target);
    }

    @Override
    public int createRenderbuffer() {
        return GL33C.glGenRenderbuffers();
    }

    @Override
    public void deleteRenderbuffer(int renderbuffer) {
        GL33C.glDeleteRenderbuffers(renderbuffer);
    }

    @Override
    public void bindRenderbuffer(int target, int renderbuffer) {
        GL33C.glBindRenderbuffer(target, renderbuffer);
    }

    @Override
    public void renderbufferStorage(int target, int internalFormat, int width, int height) {
        GL33C.glRenderbufferStorage(target, internalFormat, width, height);
    }

    @Override
    public void framebufferRenderbuffer(int target, int attachment, int renderbufferTarget, int renderbuffer) {
        GL33C.glFramebufferRenderbuffer(target, attachment, renderbufferTarget, renderbuffer);
    }

    @Override
    public void blitFramebuffer(
            int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int filter) {
        GL33C.glBlitFramebuffer(
                srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, GL33C.GL_COLOR_BUFFER_BIT, filter);
    }

    @Override
    public void readPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels) {
        GL33C.glReadPixels(x, y, width, height, format, type, pixels);
    }

    @Override
    public void drawArrays(int mode, int first, int count) {
        GL33C.glDrawArrays(mode, first, count);
    }

    @Override
    public void drawElements(int mode, int count, int type, int offsetInBytes) {
        GL33C.glDrawElements(mode, count, type, offsetInBytes);
    }

    @Override
    public void drawArraysInstanced(int mode, int first, int count, int instanceCount) {
        GL33C.glDrawArraysInstanced(mode, first, count, instanceCount);
    }

    @Override
    public void drawElementsInstanced(int mode, int count, int type, int offsetInBytes, int instanceCount) {
        GL33C.glDrawElementsInstanced(mode, count, type, offsetInBytes, instanceCount);
    }
}
