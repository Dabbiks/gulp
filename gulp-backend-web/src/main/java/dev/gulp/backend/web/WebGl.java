package dev.gulp.backend.web;

import dev.gulp.platform.Gl;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.ArrayBufferView;
import org.teavm.jso.typedarrays.Int8Array;
import org.teavm.jso.webgl.WebGL2RenderingContext;
import org.teavm.jso.webgl.WebGLBuffer;
import org.teavm.jso.webgl.WebGLFramebuffer;
import org.teavm.jso.webgl.WebGLProgram;
import org.teavm.jso.webgl.WebGLRenderbuffer;
import org.teavm.jso.webgl.WebGLShader;
import org.teavm.jso.webgl.WebGLTexture;
import org.teavm.jso.webgl.WebGLUniformLocation;
import org.teavm.jso.webgl.WebGLVertexArrayObject;

/**
 * {@link Gl} on WebGL2. WebGL hands out objects instead of integer names, so each kind of object lives in a table and
 * the engine sees its index; 0 stays "none" as in OpenGL.
 */
final class WebGl implements Gl {

    /** Maps integer handles to WebGL objects; slot 0 is always empty. */
    private static final class Table<T extends JSObject> {
        private final List<@Nullable T> objects = new ArrayList<>();
        private final List<Integer> free = new ArrayList<>();

        Table() {
            objects.add(null);
        }

        int add(@Nullable T object) {
            if (object == null) {
                return 0;
            }
            if (!free.isEmpty()) {
                int handle = free.remove(free.size() - 1);
                objects.set(handle, object);
                return handle;
            }
            objects.add(object);
            return objects.size() - 1;
        }

        @Nullable T get(int handle) {
            return handle > 0 && handle < objects.size() ? objects.get(handle) : null;
        }

        /** Puts a new object behind an existing handle, after the context was restored. */
        void set(int handle, T object) {
            while (objects.size() <= handle) {
                objects.add(null);
            }
            objects.set(handle, object);
            free.remove((Integer) handle);
        }

        @Nullable T remove(int handle) {
            T object = get(handle);
            if (object != null) {
                objects.set(handle, null);
                free.add(handle);
            }
            return object;
        }
    }

    private final WebGL2RenderingContext gl;
    private final Table<WebGLBuffer> buffers = new Table<>();
    private final Table<WebGLVertexArrayObject> vertexArrays = new Table<>();
    private final Table<WebGLShader> shaders = new Table<>();
    private final Table<WebGLProgram> programs = new Table<>();
    private final Table<WebGLUniformLocation> uniforms = new Table<>();
    private final Table<WebGLTexture> textures = new Table<>();
    private final Table<WebGLFramebuffer> framebuffers = new Table<>();
    private final Table<WebGLRenderbuffer> renderbuffers = new Table<>();

    private final GlJournal journal = new GlJournal();
    private boolean replaying;

    WebGl(WebGL2RenderingContext gl) {
        this.gl = gl;
        journal.setRelocator((handle, fresh) -> {
            WebGLUniformLocation moved = uniforms.remove(fresh);
            if (moved != null) {
                uniforms.set(handle, moved);
            }
        });
    }

    /**
     * Rebuilds every GL object behind the engine's handles after the browser restored a lost context.
     *
     * @return bytes of pixel and buffer copies that were uploaded again
     */
    long restore() {
        replaying = true;
        try {
            journal.replay(this, this::recreate);
        } finally {
            replaying = false;
        }
        return journal.shadowBytes();
    }

    private void recreate(GlJournal.Kind kind, int handle) {
        switch (kind) {
            case BUFFER -> buffers.set(handle, gl.createBuffer());
            case VERTEX_ARRAY -> vertexArrays.set(handle, gl.createVertexArray());
            case SHADER -> shaders.set(handle, gl.createShader(shaderTypes.getOrDefault(handle, Gl.VERTEX_SHADER)));
            case PROGRAM -> programs.set(handle, gl.createProgram());
            case TEXTURE -> textures.set(handle, gl.createTexture());
            case FRAMEBUFFER -> framebuffers.set(handle, gl.createFramebuffer());
            case RENDERBUFFER -> renderbuffers.set(handle, gl.createRenderbuffer());
        }
    }

    private final java.util.Map<Integer, Integer> shaderTypes = new java.util.HashMap<>();

    WebGL2RenderingContext context() {
        return gl;
    }

    @Override
    public int getError() {
        return gl.getError();
    }

    @Override
    public int getInteger(int pname) {
        return gl.getParameteri(pname);
    }

    @Override
    public @Nullable String getString(int name) {
        return Js.parameterString(gl, name);
    }

    @Override
    public void enable(int capability) {
        gl.enable(capability);
    }

    @Override
    public void disable(int capability) {
        gl.disable(capability);
    }

    @Override
    public void viewport(int x, int y, int width, int height) {
        gl.viewport(x, y, width, height);
    }

    @Override
    public void scissor(int x, int y, int width, int height) {
        gl.scissor(x, y, width, height);
    }

    @Override
    public void clearColor(float r, float g, float b, float a) {
        gl.clearColor(r, g, b, a);
    }

    @Override
    public void clear(int mask) {
        gl.clear(mask);
    }

    @Override
    public void colorMask(boolean r, boolean g, boolean b, boolean a) {
        gl.colorMask(r, g, b, a);
    }

    @Override
    public void blendFunc(int sourceFactor, int destinationFactor) {
        gl.blendFunc(sourceFactor, destinationFactor);
    }

    @Override
    public void blendFuncSeparate(int sourceRgb, int destinationRgb, int sourceAlpha, int destinationAlpha) {
        gl.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
    }

    @Override
    public void blendEquation(int mode) {
        gl.blendEquation(mode);
    }

    @Override
    public void blendEquationSeparate(int modeRgb, int modeAlpha) {
        gl.blendEquationSeparate(modeRgb, modeAlpha);
    }

    @Override
    public void pixelStorei(int pname, int value) {
        gl.pixelStorei(pname, value);
    }

    // ------------------------------------------------------------------ buffers

    @Override
    public int createBuffer() {
        int handle = buffers.add(gl.createBuffer());
        if (!replaying) {
            journal.created(GlJournal.Kind.BUFFER, handle);
        }
        return handle;
    }

    @Override
    public void deleteBuffer(int buffer) {
        WebGLBuffer object = buffers.remove(buffer);
        journal.deleted(GlJournal.Kind.BUFFER, buffer);
        if (object != null) {
            gl.deleteBuffer(object);
        }
    }

    @Override
    public void bindBuffer(int target, int buffer) {
        if (!replaying) {
            journal.bindBuffer(target, buffer);
        }
        gl.bindBuffer(target, buffers.get(buffer));
    }

    @Override
    public void bufferData(int target, ByteBuffer data, int usage) {
        if (!replaying) {
            journal.bufferData(target, data, 0, usage);
        }
        gl.bufferData(target, data, usage);
    }

    @Override
    public void bufferData(int target, int sizeInBytes, int usage) {
        if (!replaying) {
            journal.bufferData(target, null, sizeInBytes, usage);
        }
        gl.bufferData(target, sizeInBytes, usage);
    }

    @Override
    public void bufferSubData(int target, int offsetInBytes, ByteBuffer data) {
        // Streamed data (vertices and indices of each flush) is uploaded again before use, so it is not kept.
        gl.bufferSubData(target, offsetInBytes, data);
    }

    @Override
    public void bindBufferBase(int target, int index, int buffer) {
        if (!replaying) {
            journal.bindBufferBase(target, index, buffer);
        }
        gl.bindBufferBase(target, index, buffers.get(buffer));
    }

    @Override
    public int createVertexArray() {
        int handle = vertexArrays.add(gl.createVertexArray());
        if (!replaying) {
            journal.created(GlJournal.Kind.VERTEX_ARRAY, handle);
        }
        return handle;
    }

    @Override
    public void deleteVertexArray(int vertexArray) {
        WebGLVertexArrayObject object = vertexArrays.remove(vertexArray);
        journal.deleted(GlJournal.Kind.VERTEX_ARRAY, vertexArray);
        if (object != null) {
            gl.deleteVertexArray(object);
        }
    }

    @Override
    public void bindVertexArray(int vertexArray) {
        if (!replaying) {
            journal.bindVertexArray(vertexArray);
        }
        gl.bindVertexArray(vertexArrays.get(vertexArray));
    }

    @Override
    public void enableVertexAttribArray(int index) {
        if (!replaying) {
            journal.enableVertexAttribArray(index);
        }
        gl.enableVertexAttribArray(index);
    }

    @Override
    public void disableVertexAttribArray(int index) {
        if (!replaying) {
            journal.disableVertexAttribArray(index);
        }
        gl.disableVertexAttribArray(index);
    }

    @Override
    public void vertexAttribPointer(
            int index, int size, int type, boolean normalized, int strideInBytes, int offsetInBytes) {
        if (!replaying) {
            journal.vertexAttribPointer(index, size, type, normalized, strideInBytes, offsetInBytes);
        }
        gl.vertexAttribPointer(index, size, type, normalized, strideInBytes, offsetInBytes);
    }

    @Override
    public void vertexAttribDivisor(int index, int divisor) {
        if (!replaying) {
            journal.vertexAttribDivisor(index, divisor);
        }
        gl.vertexAttribDivisor(index, divisor);
    }

    // ------------------------------------------------------------------ shaders

    @Override
    public int createShader(int type) {
        int handle = shaders.add(gl.createShader(type));
        if (!replaying) {
            journal.shaderCreated(handle, type);
            shaderTypes.put(handle, type);
        }
        return handle;
    }

    @Override
    public void shaderSource(int shader, String source) {
        if (!replaying) {
            journal.shaderSource(shader, source);
        }
        gl.shaderSource(shaders.get(shader), source);
    }

    @Override
    public void compileShader(int shader) {
        gl.compileShader(shaders.get(shader));
    }

    @Override
    public int getShaderi(int shader, int pname) {
        return Js.parameterInt(gl.getShaderParameter(shaders.get(shader), pname));
    }

    @Override
    public String getShaderInfoLog(int shader) {
        String log = gl.getShaderInfoLog(shaders.get(shader));
        return log == null ? "" : log;
    }

    @Override
    public void deleteShader(int shader) {
        WebGLShader object = shaders.remove(shader);
        if (!replaying) {
            journal.deleted(GlJournal.Kind.SHADER, shader);
            shaderTypes.remove(shader);
        }
        if (object != null) {
            gl.deleteShader(object);
        }
    }

    @Override
    public int createProgram() {
        int handle = programs.add(gl.createProgram());
        if (!replaying) {
            journal.created(GlJournal.Kind.PROGRAM, handle);
        }
        return handle;
    }

    @Override
    public void attachShader(int program, int shader) {
        if (!replaying) {
            journal.attachShader(program, shader);
        }
        gl.attachShader(programs.get(program), shaders.get(shader));
    }

    @Override
    public void detachShader(int program, int shader) {
        if (!replaying) {
            journal.detachShader(program, shader);
        }
        gl.detachShader(programs.get(program), shaders.get(shader));
    }

    @Override
    public void bindAttribLocation(int program, int index, String name) {
        if (!replaying) {
            journal.bindAttribLocation(program, index, name);
        }
        gl.bindAttribLocation(programs.get(program), index, name);
    }

    @Override
    public void linkProgram(int program) {
        if (!replaying) {
            journal.linkProgram(program);
        }
        gl.linkProgram(programs.get(program));
    }

    @Override
    public int getProgrami(int program, int pname) {
        return Js.parameterInt(gl.getProgramParameter(programs.get(program), pname));
    }

    @Override
    public String getProgramInfoLog(int program) {
        String log = gl.getProgramInfoLog(programs.get(program));
        return log == null ? "" : log;
    }

    @Override
    public void useProgram(int program) {
        gl.useProgram(programs.get(program));
    }

    @Override
    public void deleteProgram(int program) {
        WebGLProgram object = programs.remove(program);
        journal.deleted(GlJournal.Kind.PROGRAM, program);
        if (object != null) {
            gl.deleteProgram(object);
        }
    }

    @Override
    public int getUniformLocation(int program, String name) {
        int handle = uniforms.add(gl.getUniformLocation(programs.get(program), name));
        if (!replaying) {
            journal.uniformLocation(handle, program, name);
        }
        return handle == 0 ? -1 : handle;
    }

    @Override
    public int getUniformBlockIndex(int program, String name) {
        return gl.getUniformBlockIndex(programs.get(program), name);
    }

    @Override
    public void uniformBlockBinding(int program, int blockIndex, int binding) {
        if (!replaying) {
            String name = gl.getActiveUniformBlockName(programs.get(program), blockIndex);
            if (name != null) {
                journal.uniformBlockBinding(program, name, binding);
            }
        }
        gl.uniformBlockBinding(programs.get(program), blockIndex, binding);
    }

    @Override
    public void uniform1i(int location, int x) {
        if (!replaying) {
            journal.uniformInt(location, x);
        }
        gl.uniform1i(uniforms.get(location), x);
    }

    @Override
    public void uniform1f(int location, float x) {
        if (!replaying) {
            journal.uniformFloats(location, 1, x, 0f, 0f, 0f);
        }
        gl.uniform1f(uniforms.get(location), x);
    }

    @Override
    public void uniform2f(int location, float x, float y) {
        if (!replaying) {
            journal.uniformFloats(location, 2, x, y, 0f, 0f);
        }
        gl.uniform2f(uniforms.get(location), x, y);
    }

    @Override
    public void uniform3f(int location, float x, float y, float z) {
        if (!replaying) {
            journal.uniformFloats(location, 3, x, y, z, 0f);
        }
        gl.uniform3f(uniforms.get(location), x, y, z);
    }

    @Override
    public void uniform4f(int location, float x, float y, float z, float w) {
        if (!replaying) {
            journal.uniformFloats(location, 4, x, y, z, w);
        }
        gl.uniform4f(uniforms.get(location), x, y, z, w);
    }

    @Override
    public void uniform1fv(int location, FloatBuffer values) {
        if (!replaying) {
            journal.uniformArray(location, 6, values);
        }
        gl.uniform1fv(uniforms.get(location), values);
    }

    @Override
    public void uniform4fv(int location, FloatBuffer values) {
        if (!replaying) {
            journal.uniformArray(location, 9, values);
        }
        gl.uniform4fv(uniforms.get(location), values);
    }

    @Override
    public void uniformMatrix3fv(int location, FloatBuffer values) {
        if (!replaying) {
            journal.uniformArray(location, 7, values);
        }
        gl.uniformMatrix3fv(uniforms.get(location), false, values);
    }

    @Override
    public void uniformMatrix4fv(int location, FloatBuffer values) {
        if (!replaying) {
            journal.uniformArray(location, 8, values);
        }
        gl.uniformMatrix4fv(uniforms.get(location), false, values);
    }

    // ------------------------------------------------------------------ textures and frame buffers

    @Override
    public int createTexture() {
        int handle = textures.add(gl.createTexture());
        if (!replaying) {
            journal.created(GlJournal.Kind.TEXTURE, handle);
        }
        return handle;
    }

    @Override
    public void deleteTexture(int texture) {
        WebGLTexture object = textures.remove(texture);
        journal.deleted(GlJournal.Kind.TEXTURE, texture);
        if (object != null) {
            gl.deleteTexture(object);
        }
    }

    @Override
    public void activeTexture(int unit) {
        if (!replaying) {
            journal.activeTexture(unit);
        }
        gl.activeTexture(unit);
    }

    @Override
    public void bindTexture(int target, int texture) {
        if (!replaying) {
            journal.bindTexture(texture);
        }
        gl.bindTexture(target, textures.get(texture));
    }

    @Override
    public void texParameteri(int target, int pname, int value) {
        if (!replaying) {
            journal.texParameteri(pname, value);
        }
        gl.texParameteri(target, pname, value);
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
        if (!replaying) {
            journal.texImage2D(level, internalFormat, width, height, format, type, pixels);
        }
        if (pixels == null) {
            gl.texImage2D(target, level, internalFormat, width, height, 0, format, type, (Int8Array) null);
        } else {
            gl.texImage2D(target, level, internalFormat, width, height, 0, format, type, bytes(pixels));
        }
    }

    @Override
    public void texSubImage2D(
            int target, int level, int x, int y, int width, int height, int format, int type, ByteBuffer pixels) {
        if (!replaying) {
            journal.texSubImage2D(level, x, y, width, height, format, type, pixels);
        }
        gl.texSubImage2D(target, level, x, y, width, height, format, type, bytes(pixels));
    }

    /** WebGL insists on Uint8Array for UNSIGNED_BYTE pixels, while TeaVM passes byte buffers as Int8Array. */
    private static ArrayBufferView bytes(ByteBuffer pixels) {
        return Js.unsigned(Int8Array.fromJavaBuffer(pixels));
    }

    @Override
    public void generateMipmap(int target) {
        if (!replaying) {
            journal.generateMipmap();
        }
        gl.generateMipmap(target);
    }

    @Override
    public int createFramebuffer() {
        int handle = framebuffers.add(gl.createFramebuffer());
        if (!replaying) {
            journal.created(GlJournal.Kind.FRAMEBUFFER, handle);
        }
        return handle;
    }

    @Override
    public void deleteFramebuffer(int framebuffer) {
        WebGLFramebuffer object = framebuffers.remove(framebuffer);
        journal.deleted(GlJournal.Kind.FRAMEBUFFER, framebuffer);
        if (object != null) {
            gl.deleteFramebuffer(object);
        }
    }

    @Override
    public void bindFramebuffer(int target, int framebuffer) {
        if (!replaying) {
            journal.bindFramebuffer(target, framebuffer);
        }
        gl.bindFramebuffer(target, framebuffers.get(framebuffer));
    }

    @Override
    public void framebufferTexture2D(int target, int attachment, int textureTarget, int texture, int level) {
        if (!replaying) {
            journal.framebufferAttachment(attachment, 0, texture);
        }
        gl.framebufferTexture2D(target, attachment, textureTarget, textures.get(texture), level);
    }

    @Override
    public int checkFramebufferStatus(int target) {
        return gl.checkFramebufferStatus(target);
    }

    @Override
    public int createRenderbuffer() {
        int handle = renderbuffers.add(gl.createRenderbuffer());
        if (!replaying) {
            journal.created(GlJournal.Kind.RENDERBUFFER, handle);
        }
        return handle;
    }

    @Override
    public void deleteRenderbuffer(int renderbuffer) {
        WebGLRenderbuffer object = renderbuffers.remove(renderbuffer);
        journal.deleted(GlJournal.Kind.RENDERBUFFER, renderbuffer);
        if (object != null) {
            gl.deleteRenderbuffer(object);
        }
    }

    @Override
    public void bindRenderbuffer(int target, int renderbuffer) {
        if (!replaying) {
            journal.bindRenderbuffer(renderbuffer);
        }
        gl.bindRenderbuffer(target, renderbuffers.get(renderbuffer));
    }

    @Override
    public void renderbufferStorage(int target, int internalFormat, int width, int height) {
        if (!replaying) {
            journal.renderbufferStorage(internalFormat, width, height);
        }
        gl.renderbufferStorage(target, internalFormat, width, height);
    }

    @Override
    public void framebufferRenderbuffer(int target, int attachment, int renderbufferTarget, int renderbuffer) {
        if (!replaying) {
            journal.framebufferAttachment(attachment, 1, renderbuffer);
        }
        gl.framebufferRenderbuffer(target, attachment, renderbufferTarget, renderbuffers.get(renderbuffer));
    }

    @Override
    public void blitFramebuffer(
            int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int filter) {
        gl.blitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, Gl.COLOR_BUFFER_BIT, filter);
    }

    @Override
    public void readPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels) {
        Int8Array target = new Int8Array(width * height * 4);
        // WebGL wants a Uint8Array for UNSIGNED_BYTE; the view shares the buffer, so target sees the pixels.
        gl.readPixels(x, y, width, height, format, type, Js.unsigned(target));
        byte[] copy = target.copyToJavaArray();
        pixels.duplicate().put(copy, 0, Math.min(copy.length, pixels.remaining()));
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void drawArrays(int mode, int first, int count) {
        gl.drawArrays(mode, first, count);
    }

    @Override
    public void drawElements(int mode, int count, int type, int offsetInBytes) {
        gl.drawElements(mode, count, type, offsetInBytes);
    }

    @Override
    public void drawArraysInstanced(int mode, int first, int count, int instanceCount) {
        gl.drawArraysInstanced(mode, first, count, instanceCount);
    }

    @Override
    public void drawElementsInstanced(int mode, int count, int type, int offsetInBytes, int instanceCount) {
        gl.drawElementsInstanced(mode, count, type, offsetInBytes, instanceCount);
    }
}
