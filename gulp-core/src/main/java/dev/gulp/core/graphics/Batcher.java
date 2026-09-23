package dev.gulp.core.graphics;

import dev.gulp.api.graphics.BlendMode;
import dev.gulp.api.math.Affine2;
import dev.gulp.platform.Gl;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Collects triangles with position, texture coordinates and a packed premultiplied color, and draws them in as few
 * calls as possible. A flush happens only when the texture, shader, blend mode or projection changes, when the buffer
 * is full, or when the caller asks. Two vertex buffers alternate between flushes so the GPU is never waited on.
 * Adding vertices does not allocate.
 */
public final class Batcher {

    /** Largest number of quads per flush. */
    public static final int MAX_QUADS = 16_384;

    static final int MAX_VERTICES = MAX_QUADS * 4;
    static final int MAX_INDICES = MAX_QUADS * 6;
    static final int VERTEX_BYTES = 20;

    private final Gl gl;
    private final ByteBuffer vertices =
            ByteBuffer.allocateDirect(MAX_VERTICES * VERTEX_BYTES).order(ByteOrder.nativeOrder());
    private final ByteBuffer indices =
            ByteBuffer.allocateDirect(MAX_INDICES * 2).order(ByteOrder.nativeOrder());
    private final int[] vertexArrays = new int[2];
    private final int[] vertexBuffers = new int[2];
    private final int[] indexBuffers = new int[2];
    private int current;

    private int vertexCount;
    private int indexCount;

    private int texture;
    private ShaderImpl shader;
    private final ShaderImpl defaultShader;
    private BlendMode blend = BlendMode.NORMAL;
    private final float[] projection = new float[9];
    private final int[] extraTextures = new int[8];

    private int drawCalls;
    private int textureBinds;
    private int submittedVertices;
    private int flushes;
    private int boundTexture = -1;

    /**
     * Creates the GPU buffers.
     *
     * @param gl the graphics context
     * @param defaultShader shader used when none is set or a custom one is invalid
     */
    public Batcher(Gl gl, ShaderImpl defaultShader) {
        this.gl = gl;
        this.defaultShader = defaultShader;
        this.shader = defaultShader;
        projection[0] = 1f;
        projection[4] = 1f;
        projection[8] = 1f;
        for (int i = 0; i < 2; i++) {
            vertexArrays[i] = gl.createVertexArray();
            gl.bindVertexArray(vertexArrays[i]);
            vertexBuffers[i] = gl.createBuffer();
            gl.bindBuffer(Gl.ARRAY_BUFFER, vertexBuffers[i]);
            gl.bufferData(Gl.ARRAY_BUFFER, MAX_VERTICES * VERTEX_BYTES, Gl.DYNAMIC_DRAW);
            gl.enableVertexAttribArray(ShaderImpl.POSITION);
            gl.vertexAttribPointer(ShaderImpl.POSITION, 2, Gl.FLOAT, false, VERTEX_BYTES, 0);
            gl.enableVertexAttribArray(ShaderImpl.TEX_COORD);
            gl.vertexAttribPointer(ShaderImpl.TEX_COORD, 2, Gl.FLOAT, false, VERTEX_BYTES, 8);
            gl.enableVertexAttribArray(ShaderImpl.COLOR);
            gl.vertexAttribPointer(ShaderImpl.COLOR, 4, Gl.UNSIGNED_BYTE, true, VERTEX_BYTES, 16);
            indexBuffers[i] = gl.createBuffer();
            gl.bindBuffer(Gl.ELEMENT_ARRAY_BUFFER, indexBuffers[i]);
            gl.bufferData(Gl.ELEMENT_ARRAY_BUFFER, MAX_INDICES * 2, Gl.DYNAMIC_DRAW);
        }
        gl.bindVertexArray(0);
    }

    // ------------------------------------------------------------------ state

    /**
     * Uses a texture on unit 0.
     *
     * @param handle the texture handle
     */
    public void texture(int handle) {
        if (handle != texture) {
            flush();
            texture = handle;
        }
    }

    /**
     * Uses a shader; {@code null} or an invalid shader selects the default one.
     *
     * @param newShader the shader
     */
    public void shader(ShaderImpl newShader) {
        ShaderImpl resolved = newShader.isValid() && !newShader.isDisposed() ? newShader : defaultShader;
        if (resolved != shader) {
            flush();
            shader = resolved;
        }
    }

    /**
     * Uses a blend mode.
     *
     * @param mode the blend mode
     */
    public void blend(BlendMode mode) {
        if (mode != blend) {
            flush();
            blend = mode;
        }
    }

    /**
     * Binds an extra texture for the next flush.
     *
     * @param unit the unit, 1..7
     * @param handle the texture handle, or 0 for none
     */
    public void extraTexture(int unit, int handle) {
        if (extraTextures[unit] != handle) {
            flush();
            extraTextures[unit] = handle;
        }
    }

    /**
     * Sets the projection from drawing coordinates to clip space.
     *
     * @param affine the projection
     */
    public void projection(Affine2 affine) {
        if (projection[0] == affine.m00
                && projection[1] == affine.m10
                && projection[3] == affine.m01
                && projection[4] == affine.m11
                && projection[6] == affine.m02
                && projection[7] == affine.m12) {
            return;
        }
        flush();
        projection[0] = affine.m00;
        projection[1] = affine.m10;
        projection[2] = 0f;
        projection[3] = affine.m01;
        projection[4] = affine.m11;
        projection[5] = 0f;
        projection[6] = affine.m02;
        projection[7] = affine.m12;
        projection[8] = 1f;
    }

    // ------------------------------------------------------------------ geometry

    /**
     * Makes room for vertices and indices, flushing if the buffer is full.
     *
     * @param vertexNeed vertices about to be added
     * @param indexNeed indices about to be added
     * @return the index of the first new vertex
     */
    public int reserve(int vertexNeed, int indexNeed) {
        if (vertexCount + vertexNeed > MAX_VERTICES || indexCount + indexNeed > MAX_INDICES) {
            flush();
        }
        return vertexCount;
    }

    /**
     * Adds a vertex.
     *
     * @param x position X, already transformed
     * @param y position Y, already transformed
     * @param u texture U
     * @param v texture V
     * @param abgr premultiplied color, {@code 0xAABBGGRR}
     */
    public void vertex(float x, float y, float u, float v, int abgr) {
        int o = vertexCount * VERTEX_BYTES;
        vertices.putFloat(o, x);
        vertices.putFloat(o + 4, y);
        vertices.putFloat(o + 8, u);
        vertices.putFloat(o + 12, v);
        vertices.putInt(o + 16, abgr);
        vertexCount++;
    }

    /**
     * Adds a triangle by vertex indices.
     *
     * @param a first vertex
     * @param b second vertex
     * @param c third vertex
     */
    public void triangle(int a, int b, int c) {
        int o = indexCount * 2;
        indices.putShort(o, (short) a);
        indices.putShort(o + 2, (short) b);
        indices.putShort(o + 4, (short) c);
        indexCount += 3;
    }

    /**
     * Adds two triangles forming a quad from four consecutive vertices.
     *
     * @param first index of the first of the four vertices, in order around the quad
     */
    public void quad(int first) {
        triangle(first, first + 1, first + 2);
        triangle(first, first + 2, first + 3);
    }

    /** Draws everything collected so far. */
    public void flush() {
        flushes++;
        if (indexCount == 0) {
            vertexCount = 0;
            return;
        }
        shader.bind(projection);
        if (boundTexture != texture) {
            gl.activeTexture(Gl.TEXTURE0);
            gl.bindTexture(Gl.TEXTURE_2D, texture);
            boundTexture = texture;
            textureBinds++;
        }
        for (int unit = 1; unit < extraTextures.length; unit++) {
            if (extraTextures[unit] != 0) {
                gl.activeTexture(Gl.TEXTURE0 + unit);
                gl.bindTexture(Gl.TEXTURE_2D, extraTextures[unit]);
                boundTexture = -1;
            }
        }
        gl.activeTexture(Gl.TEXTURE0);
        applyBlend();
        gl.bindVertexArray(vertexArrays[current]);
        gl.bindBuffer(Gl.ARRAY_BUFFER, vertexBuffers[current]);
        vertices.position(0).limit(vertexCount * VERTEX_BYTES);
        gl.bufferSubData(Gl.ARRAY_BUFFER, 0, vertices);
        vertices.clear();
        gl.bindBuffer(Gl.ELEMENT_ARRAY_BUFFER, indexBuffers[current]);
        indices.position(0).limit(indexCount * 2);
        gl.bufferSubData(Gl.ELEMENT_ARRAY_BUFFER, 0, indices);
        indices.clear();
        gl.drawElements(Gl.TRIANGLES, indexCount, Gl.UNSIGNED_SHORT, 0);
        gl.bindVertexArray(0);
        drawCalls++;
        submittedVertices += vertexCount;
        vertexCount = 0;
        indexCount = 0;
        current ^= 1;
    }

    private void applyBlend() {
        if (blend == BlendMode.REPLACE) {
            gl.disable(Gl.BLEND);
            return;
        }
        gl.enable(Gl.BLEND);
        switch (blend) {
            case ADD -> gl.blendFunc(Gl.ONE, Gl.ONE);
            case MULTIPLY -> gl.blendFunc(Gl.DST_COLOR, Gl.ONE_MINUS_SRC_ALPHA);
            case SCREEN -> gl.blendFunc(Gl.ONE, Gl.ONE_MINUS_SRC_COLOR);
            default -> gl.blendFunc(Gl.ONE, Gl.ONE_MINUS_SRC_ALPHA);
        }
    }

    /** Resets per-frame counters and forgets GL state that others may have changed. */
    public void beginFrame() {
        drawCalls = 0;
        textureBinds = 0;
        submittedVertices = 0;
        flushes = 0;
        boundTexture = -1;
    }

    /** Forgets the bound texture, after code outside the batcher changed GL state. */
    public void invalidateState() {
        boundTexture = -1;
    }

    /**
     * Draw calls since {@link #beginFrame()}.
     *
     * @return the count
     */
    public int drawCalls() {
        return drawCalls;
    }

    /**
     * Texture binds since {@link #beginFrame()}.
     *
     * @return the count
     */
    public int textureBinds() {
        return textureBinds;
    }

    /**
     * Vertices drawn since {@link #beginFrame()}.
     *
     * @return the count
     */
    public int vertices() {
        return submittedVertices;
    }

    /**
     * Flushes since {@link #beginFrame()}.
     *
     * @return the count
     */
    public int flushes() {
        return flushes;
    }

    /**
     * Pending vertices not yet drawn.
     *
     * @return the count
     */
    public int pendingVertices() {
        return vertexCount;
    }

    /** Frees the GPU buffers. */
    public void dispose() {
        for (int i = 0; i < 2; i++) {
            gl.deleteVertexArray(vertexArrays[i]);
            gl.deleteBuffer(vertexBuffers[i]);
            gl.deleteBuffer(indexBuffers[i]);
        }
    }
}
