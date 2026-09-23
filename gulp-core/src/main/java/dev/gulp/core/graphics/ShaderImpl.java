package dev.gulp.core.graphics;

import dev.gulp.api.Logger;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Shader;
import dev.gulp.api.math.Mat3;
import dev.gulp.api.math.Vec2;
import dev.gulp.platform.Gl;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * GLSL ES 3.00 program with the engine's attribute layout ({@code a_position}, {@code a_texCoord}, {@code a_color})
 * and uniforms ({@code u_projection}, {@code u_texture}). Uniform values are stored and re-applied on every bind.
 */
public final class ShaderImpl implements Shader {

    /** Attribute locations shared by every shader. */
    static final int POSITION = 0;

    static final int TEX_COORD = 1;
    static final int COLOR = 2;

    static final String DEFAULT_VERTEX = """
            #version 300 es
            in vec2 a_position;
            in vec2 a_texCoord;
            in vec4 a_color;
            uniform mat3 u_projection;
            out vec2 v_texCoord;
            out vec4 v_color;
            void main() {
                v_texCoord = a_texCoord;
                v_color = a_color;
                vec3 p = u_projection * vec3(a_position, 1.0);
                gl_Position = vec4(p.xy, 0.0, 1.0);
            }
            """;

    static final String COMMON_INCLUDE = """
            precision mediump float;
            in vec2 v_texCoord;
            in vec4 v_color;
            uniform sampler2D u_texture;
            out vec4 fragColor;
            """;

    static final String DEFAULT_FRAGMENT = """
            #include "gulp:common.glsl"
            void main() {
                fragColor = texture(u_texture, v_texCoord) * v_color;
            }
            """;

    private final Gl gl;
    private final int program;
    private final boolean valid;
    private final String log;
    private final Map<String, Integer> locations = new HashMap<>();
    private final Map<String, Object> uniforms = new LinkedHashMap<>();
    private final FloatBuffer matrix =
            ByteBuffer.allocateDirect(9 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private final float[] columns = new float[9];
    private boolean disposed;

    ShaderImpl(Gl gl, String vertexSource, String fragmentSource, Map<String, String> includes, Logger logger) {
        this.gl = gl;
        StringBuilder messages = new StringBuilder();
        String vertex = prepare(vertexSource, includes);
        String fragment = prepare(fragmentSource, includes);
        int vs = compile(Gl.VERTEX_SHADER, vertex, "vertex", messages);
        int fs = compile(Gl.FRAGMENT_SHADER, fragment, "fragment", messages);
        int handle = 0;
        boolean ok = vs != 0 && fs != 0;
        if (ok) {
            handle = gl.createProgram();
            gl.attachShader(handle, vs);
            gl.attachShader(handle, fs);
            gl.bindAttribLocation(handle, POSITION, "a_position");
            gl.bindAttribLocation(handle, TEX_COORD, "a_texCoord");
            gl.bindAttribLocation(handle, COLOR, "a_color");
            gl.linkProgram(handle);
            gl.detachShader(handle, vs);
            gl.detachShader(handle, fs);
            if (gl.getProgrami(handle, Gl.LINK_STATUS) != Gl.TRUE) {
                messages.append("Link failed: ")
                        .append(gl.getProgramInfoLog(handle))
                        .append('\n');
                gl.deleteProgram(handle);
                handle = 0;
                ok = false;
            }
        }
        gl.deleteShader(vs);
        gl.deleteShader(fs);
        this.program = handle;
        this.valid = ok;
        this.log = messages.toString().trim();
        if (!ok) {
            logger.error("Shader failed to compile; using the default shader instead.\n" + log);
        }
    }

    /**
     * Expands includes and adds the {@code #version} line if missing.
     *
     * @param source the source
     * @param includes include name to text
     * @return the prepared source
     */
    static String prepare(String source, Map<String, String> includes) {
        String expanded = expand(source, includes, 0);
        return expanded.stripLeading().startsWith("#version") ? expanded : "#version 300 es\n" + expanded;
    }

    private static String expand(String source, Map<String, String> includes, int depth) {
        if (depth > 8) {
            throw new IllegalArgumentException("Shader includes nested too deeply");
        }
        StringBuilder out = new StringBuilder();
        for (String line : source.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#include")) {
                int open = trimmed.indexOf('"');
                int close = trimmed.lastIndexOf('"');
                String name = open >= 0 && close > open ? trimmed.substring(open + 1, close) : "";
                String text = includes.get(name);
                if (text == null) {
                    throw new IllegalArgumentException("Unknown shader include '" + name + "'");
                }
                out.append(expand(text, includes, depth + 1));
            } else {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    private int compile(int type, String source, String kind, StringBuilder messages) {
        int shader = gl.createShader(type);
        gl.shaderSource(shader, source);
        gl.compileShader(shader);
        if (gl.getShaderi(shader, Gl.COMPILE_STATUS) != Gl.TRUE) {
            messages.append(kind)
                    .append(" shader: ")
                    .append(gl.getShaderInfoLog(shader))
                    .append('\n')
                    .append(numbered(source));
            gl.deleteShader(shader);
            return 0;
        }
        return shader;
    }

    /**
     * Prefixes every line with its number, for error messages.
     *
     * @param source the source
     * @return the numbered source
     */
    static String numbered(String source) {
        StringBuilder out = new StringBuilder();
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            out.append(i + 1).append(": ").append(lines[i]).append('\n');
        }
        return out.toString();
    }

    int program() {
        return program;
    }

    /**
     * Binds the program and applies the projection and stored uniforms.
     *
     * @param projection the affine projection as a column-major mat3
     */
    void bind(float[] projection) {
        gl.useProgram(program);
        matrix.clear();
        matrix.put(projection).flip();
        gl.uniformMatrix3fv(location("u_projection"), matrix);
        gl.uniform1i(location("u_texture"), 0);
        for (Map.Entry<String, Object> uniform : uniforms.entrySet()) {
            apply(location(uniform.getKey()), uniform.getValue());
        }
    }

    private void apply(int location, Object value) {
        if (location < 0) {
            return;
        }
        switch (value) {
            case Integer i -> gl.uniform1i(location, i);
            case Float f -> gl.uniform1f(location, f);
            case Vec2 v -> gl.uniform2f(location, v.x(), v.y());
            case Color c -> gl.uniform4f(location, c.r(), c.g(), c.b(), c.a());
            case Mat3 m -> {
                m.toColumnMajor(columns);
                matrix.clear();
                matrix.put(columns).flip();
                gl.uniformMatrix3fv(location, matrix);
            }
            default -> {}
        }
    }

    private int location(String name) {
        Integer cached = locations.get(name);
        if (cached == null) {
            cached = program == 0 ? -1 : gl.getUniformLocation(program, name);
            locations.put(name, cached);
        }
        return cached;
    }

    private Shader store(String name, Object value) {
        uniforms.put(name, value);
        return this;
    }

    @Override
    public Shader set(String name, int value) {
        return store(name, value);
    }

    @Override
    public Shader set(String name, float value) {
        return store(name, value);
    }

    @Override
    public Shader set(String name, Vec2 value) {
        return store(name, value);
    }

    @Override
    public Shader set(String name, Color value) {
        return store(name, value);
    }

    @Override
    public Shader set(String name, Mat3 value) {
        return store(name, value);
    }

    /**
     * A stored uniform value.
     *
     * @param name the uniform name
     * @return the value, or {@code null}
     */
    @Nullable Object uniform(String name) {
        return uniforms.get(name);
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    @Override
    public String log() {
        return log;
    }

    @Override
    public void dispose() {
        if (!disposed) {
            disposed = true;
            gl.deleteProgram(program);
        }
    }

    boolean isDisposed() {
        return disposed;
    }
}
