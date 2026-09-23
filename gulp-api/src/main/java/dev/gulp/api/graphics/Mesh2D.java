package dev.gulp.api.graphics;

import java.util.Arrays;

/**
 * Custom triangles with positions, texture coordinates and colors, drawn with {@code Draw.mesh}. Useful for terrain,
 * trails and effects.
 *
 * <pre>{@code
 * Mesh2D quad = new Mesh2D();
 * int a = quad.vertex(0, 0, 0, 0, Color.WHITE);
 * int b = quad.vertex(1, 0, 1, 0, Color.WHITE);
 * int c = quad.vertex(1, 1, 1, 1, Color.RED);
 * int d = quad.vertex(0, 1, 0, 1, Color.RED);
 * quad.triangle(a, b, c).triangle(a, c, d);
 * draw.mesh(quad, texture);
 * }</pre>
 */
public final class Mesh2D {

    private float[] vertices = new float[64];
    private int[] colors = new int[16];
    private int[] indices = new int[48];
    private int vertexCount;
    private int indexCount;

    /** Creates an empty mesh. */
    public Mesh2D() {}

    /**
     * Adds a vertex.
     *
     * @param x position X
     * @param y position Y
     * @param u texture coordinate U
     * @param v texture coordinate V
     * @param color vertex color (multiplied with the texture)
     * @return the vertex index
     */
    public int vertex(float x, float y, float u, float v, Color color) {
        if ((vertexCount + 1) * 4 > vertices.length) {
            vertices = Arrays.copyOf(vertices, vertices.length * 2);
            colors = Arrays.copyOf(colors, colors.length * 2);
        }
        int o = vertexCount * 4;
        vertices[o] = x;
        vertices[o + 1] = y;
        vertices[o + 2] = u;
        vertices[o + 3] = v;
        colors[vertexCount] = color.toPremultipliedAbgr();
        return vertexCount++;
    }

    /**
     * Adds a triangle.
     *
     * @param a first vertex index
     * @param b second vertex index
     * @param c third vertex index
     * @return this mesh
     * @throws IndexOutOfBoundsException if an index is not a vertex of this mesh
     */
    public Mesh2D triangle(int a, int b, int c) {
        for (int index : new int[] {a, b, c}) {
            if (index < 0 || index >= vertexCount) {
                throw new IndexOutOfBoundsException("No vertex " + index);
            }
        }
        if (indexCount + 3 > indices.length) {
            indices = Arrays.copyOf(indices, indices.length * 2);
        }
        indices[indexCount++] = a;
        indices[indexCount++] = b;
        indices[indexCount++] = c;
        return this;
    }

    /** Removes all vertices and triangles, keeping the memory. */
    public void clear() {
        vertexCount = 0;
        indexCount = 0;
    }

    /**
     * Number of vertices.
     *
     * @return the count
     */
    public int vertexCount() {
        return vertexCount;
    }

    /**
     * Number of indices (three per triangle).
     *
     * @return the count
     */
    public int indexCount() {
        return indexCount;
    }

    /**
     * Position X of a vertex.
     *
     * @param vertex the vertex index
     * @return X
     */
    public float x(int vertex) {
        return vertices[vertex * 4];
    }

    /**
     * Position Y of a vertex.
     *
     * @param vertex the vertex index
     * @return Y
     */
    public float y(int vertex) {
        return vertices[vertex * 4 + 1];
    }

    /**
     * Texture U of a vertex.
     *
     * @param vertex the vertex index
     * @return U
     */
    public float u(int vertex) {
        return vertices[vertex * 4 + 2];
    }

    /**
     * Texture V of a vertex.
     *
     * @param vertex the vertex index
     * @return V
     */
    public float v(int vertex) {
        return vertices[vertex * 4 + 3];
    }

    /**
     * Color of a vertex.
     *
     * @param vertex the vertex index
     * @return premultiplied color packed as {@code 0xAABBGGRR}
     */
    public int packedColor(int vertex) {
        return colors[vertex];
    }

    /**
     * A vertex index of the triangle list.
     *
     * @param i position in the index list
     * @return the vertex index
     */
    public int index(int i) {
        return indices[i];
    }
}
