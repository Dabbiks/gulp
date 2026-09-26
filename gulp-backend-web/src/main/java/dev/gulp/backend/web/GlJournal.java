package dev.gulp.backend.web;

import dev.gulp.platform.Gl;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Remembers what every GL object needs so it can be rebuilt after the browser loses and restores the WebGL context
 * (section 20.3): buffer contents, texture pixels and parameters, shader sources, program links, uniform values,
 * vertex array layouts and frame buffer attachments. The engine keeps its integer handles; {@link #replay} creates new
 * objects behind the same handles. Plain Java, so it is tested without a browser.
 *
 * <pre>{@code
 * journal.texImage2D(Gl.TEXTURE_2D, 0, Gl.RGBA8, 64, 64, Gl.RGBA, Gl.UNSIGNED_BYTE, pixels);
 * // ... webglcontextrestored ...
 * journal.replay(webGl, webGl::recreate);
 * }</pre>
 */
final class GlJournal {

    /** Kinds of GL objects. */
    enum Kind {
        BUFFER,
        VERTEX_ARRAY,
        SHADER,
        PROGRAM,
        TEXTURE,
        FRAMEBUFFER,
        RENDERBUFFER
    }

    /** Creates a fresh object for a handle during replay. */
    interface Recreator {
        /**
         * Replaces the object behind a handle with a new one.
         *
         * @param kind the kind
         * @param handle the handle the engine uses
         */
        void recreate(Kind kind, int handle);
    }

    private static final class BufferState {
        int target;
        int usage;
        byte @Nullable [] data;
        int size;
    }

    private static final class TextureState {
        int internalFormat;
        int width;
        int height;
        int format;
        int type;
        byte @Nullable [] data;
        boolean mipmaps;
        final int[] parameters = new int[8];
        int parameterCount;
    }

    private static final class ShaderState {
        final int type;
        String source = "";

        ShaderState(int type) {
            this.type = type;
        }
    }

    private static final class ProgramState {
        final List<Integer> shaders = new ArrayList<>();
        final List<Object> attributes = new ArrayList<>();
        final List<int[]> blockBindings = new ArrayList<>();
        final List<String> blockNames = new ArrayList<>();
        boolean linked;
    }

    /** Last value of a uniform: kind 1..4 floats, 5 int, 6 float array, 7 mat3, 8 mat4. */
    private static final class UniformState {
        final int program;
        final String name;
        int kind;
        int intValue;
        float[] values = new float[4];
        int count;

        UniformState(int program, String name) {
            this.program = program;
            this.name = name;
        }
    }

    private static final class VertexArrayState {
        final List<int[]> operations = new ArrayList<>();
    }

    private static final int ENABLE = 0;
    private static final int DISABLE = 1;
    private static final int POINTER = 2;
    private static final int DIVISOR = 3;
    private static final int ELEMENTS = 4;

    private @Nullable BufferState[] buffers = new BufferState[16];
    private @Nullable TextureState[] textures = new TextureState[16];
    private @Nullable ShaderState[] shaders = new ShaderState[16];
    private @Nullable ProgramState[] programs = new ProgramState[16];
    private @Nullable UniformState[] uniforms = new UniformState[16];
    private @Nullable VertexArrayState[] vertexArrays = new VertexArrayState[16];
    private @Nullable List<int[]>[] framebuffers = newLists(16);
    private int @Nullable [][] renderbuffers = new int[16][];

    private int arrayBuffer;
    private int elementBuffer;
    private int uniformBuffer;
    private int vertexArray;
    private int activeUnit;
    private final int[] unitTextures = new int[32];
    private int drawFramebuffer;
    private int renderbuffer;
    private final List<int[]> bufferBases = new ArrayList<>();

    @SuppressWarnings("unchecked")
    private static @Nullable List<int[]>[] newLists(int size) {
        return (List<int[]>[]) new List<?>[size];
    }

    private static <T> T[] grow(T[] array, int index) {
        return index < array.length ? array : Arrays.copyOf(array, Math.max(index + 1, array.length * 2));
    }

    // ================================================================== recording

    void created(Kind kind, int handle) {
        if (handle <= 0) {
            return;
        }
        switch (kind) {
            case BUFFER -> {
                buffers = grow(buffers, handle);
                buffers[handle] = new BufferState();
            }
            case TEXTURE -> {
                textures = grow(textures, handle);
                textures[handle] = new TextureState();
            }
            case PROGRAM -> {
                programs = grow(programs, handle);
                programs[handle] = new ProgramState();
            }
            case VERTEX_ARRAY -> {
                vertexArrays = grow(vertexArrays, handle);
                vertexArrays[handle] = new VertexArrayState();
            }
            case FRAMEBUFFER -> {
                framebuffers = grow(framebuffers, handle);
                framebuffers[handle] = new ArrayList<>();
            }
            case RENDERBUFFER -> {
                renderbuffers = grow(renderbuffers, handle);
                renderbuffers[handle] = new int[3];
            }
            case SHADER -> throw new IllegalArgumentException("Use shaderCreated for shaders");
        }
    }

    void shaderCreated(int handle, int type) {
        if (handle > 0) {
            shaders = grow(shaders, handle);
            shaders[handle] = new ShaderState(type);
        }
    }

    void deleted(Kind kind, int handle) {
        if (handle <= 0) {
            return;
        }
        switch (kind) {
            case BUFFER -> clear(buffers, handle);
            case TEXTURE -> clear(textures, handle);
            case SHADER -> clear(shaders, handle);
            case PROGRAM -> {
                clear(programs, handle);
                for (int i = 0; i < uniforms.length; i++) {
                    UniformState uniform = uniforms[i];
                    if (uniform != null && uniform.program == handle) {
                        uniforms[i] = null;
                    }
                }
            }
            case VERTEX_ARRAY -> clear(vertexArrays, handle);
            case FRAMEBUFFER -> clear(framebuffers, handle);
            case RENDERBUFFER -> clear(renderbuffers, handle);
        }
    }

    private static void clear(@Nullable Object[] array, int handle) {
        if (handle < array.length) {
            array[handle] = null;
        }
    }

    void bindBuffer(int target, int buffer) {
        if (target == Gl.ARRAY_BUFFER) {
            arrayBuffer = buffer;
        } else if (target == Gl.ELEMENT_ARRAY_BUFFER) {
            elementBuffer = buffer;
            recordVertexArray(ELEMENTS, buffer, 0, 0, 0, 0, 0);
        } else if (target == Gl.UNIFORM_BUFFER) {
            uniformBuffer = buffer;
        }
    }

    private @Nullable BufferState bound(int target) {
        int handle = target == Gl.ARRAY_BUFFER
                ? arrayBuffer
                : target == Gl.ELEMENT_ARRAY_BUFFER ? elementBuffer : target == Gl.UNIFORM_BUFFER ? uniformBuffer : 0;
        return handle > 0 && handle < buffers.length ? buffers[handle] : null;
    }

    void bufferData(int target, @Nullable ByteBuffer data, int size, int usage) {
        BufferState state = bound(target);
        if (state == null) {
            return;
        }
        state.target = target;
        state.usage = usage;
        if (data == null) {
            state.size = size;
            state.data = null;
        } else {
            byte[] copy = new byte[data.remaining()];
            data.duplicate().get(copy);
            state.data = copy;
            state.size = copy.length;
        }
    }

    void bindBufferBase(int target, int index, int buffer) {
        for (int i = 0; i < bufferBases.size(); i++) {
            int[] base = bufferBases.get(i);
            if (base[0] == target && base[1] == index) {
                base[2] = buffer;
                return;
            }
        }
        bufferBases.add(new int[] {target, index, buffer});
    }

    void bindVertexArray(int handle) {
        vertexArray = handle;
    }

    void enableVertexAttribArray(int index) {
        recordVertexArray(ENABLE, index, 0, 0, 0, 0, 0);
    }

    void disableVertexAttribArray(int index) {
        recordVertexArray(DISABLE, index, 0, 0, 0, 0, 0);
    }

    void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, int offset) {
        recordVertexArray(POINTER, index, size, type, normalized ? 1 : 0, stride, offset);
    }

    void vertexAttribDivisor(int index, int divisor) {
        recordVertexArray(DIVISOR, index, divisor, 0, 0, 0, 0);
    }

    private void recordVertexArray(int operation, int a, int b, int c, int d, int e, int f) {
        VertexArrayState state =
                vertexArray > 0 && vertexArray < vertexArrays.length ? vertexArrays[vertexArray] : null;
        if (state == null) {
            return;
        }
        // A layout is set up once; keep only the latest operation for each attribute and kind.
        List<int[]> operations = state.operations;
        for (int i = 0; i < operations.size(); i++) {
            int[] existing = operations.get(i);
            if (existing[0] == operation && (operation == ELEMENTS || existing[1] == a)) {
                operations.remove(i);
                break;
            }
            if ((operation == ENABLE && existing[0] == DISABLE || operation == DISABLE && existing[0] == ENABLE)
                    && existing[1] == a) {
                operations.remove(i);
                break;
            }
        }
        operations.add(new int[] {operation, a, b, c, d, e, f, operation == POINTER ? arrayBuffer : 0});
    }

    void shaderSource(int shader, String source) {
        ShaderState state = shader > 0 && shader < shaders.length ? shaders[shader] : null;
        if (state != null) {
            state.source = source;
        }
    }

    void attachShader(int program, int shader) {
        ProgramState state = program(program);
        if (state != null && !state.shaders.contains(shader)) {
            state.shaders.add(shader);
        }
    }

    void detachShader(int program, int shader) {
        ProgramState state = program(program);
        if (state != null) {
            state.shaders.remove((Integer) shader);
        }
    }

    void bindAttribLocation(int program, int index, String name) {
        ProgramState state = program(program);
        if (state != null) {
            state.attributes.add(index);
            state.attributes.add(name);
        }
    }

    void linkProgram(int program) {
        ProgramState state = program(program);
        if (state != null) {
            state.linked = true;
            // Shaders may be deleted after linking; keep their sources for the replay.
            for (int shader : state.shaders) {
                ShaderState source = shader < shaders.length ? shaders[shader] : null;
                if (source != null) {
                    state.attributes.add(-1 - source.type);
                    state.attributes.add(source.source);
                }
            }
        }
    }

    void uniformBlockBinding(int program, String blockName, int binding) {
        ProgramState state = program(program);
        if (state != null) {
            state.blockNames.add(blockName);
            state.blockBindings.add(new int[] {binding});
        }
    }

    private @Nullable ProgramState program(int handle) {
        return handle > 0 && handle < programs.length ? programs[handle] : null;
    }

    void uniformLocation(int location, int program, String name) {
        if (location > 0) {
            uniforms = grow(uniforms, location);
            uniforms[location] = new UniformState(program, name);
        }
    }

    private @Nullable UniformState uniform(int location) {
        return location > 0 && location < uniforms.length ? uniforms[location] : null;
    }

    void uniformInt(int location, int value) {
        UniformState state = uniform(location);
        if (state != null) {
            state.kind = 5;
            state.intValue = value;
        }
    }

    void uniformFloats(int location, int count, float x, float y, float z, float w) {
        UniformState state = uniform(location);
        if (state != null) {
            state.kind = count;
            state.values[0] = x;
            state.values[1] = y;
            state.values[2] = z;
            state.values[3] = w;
        }
    }

    void uniformArray(int location, int kind, FloatBuffer values) {
        UniformState state = uniform(location);
        if (state != null) {
            int count = values.remaining();
            if (state.values.length < count) {
                state.values = new float[count];
            }
            int start = values.position();
            for (int i = 0; i < count; i++) {
                state.values[i] = values.get(start + i);
            }
            state.count = count;
            state.kind = kind;
        }
    }

    void activeTexture(int unit) {
        activeUnit = Math.max(0, Math.min(unitTextures.length - 1, unit - Gl.TEXTURE0));
    }

    void bindTexture(int texture) {
        unitTextures[activeUnit] = texture;
    }

    private @Nullable TextureState boundTexture() {
        int handle = unitTextures[activeUnit];
        return handle > 0 && handle < textures.length ? textures[handle] : null;
    }

    void texParameteri(int pname, int value) {
        TextureState state = boundTexture();
        if (state == null) {
            return;
        }
        for (int i = 0; i < state.parameterCount; i += 2) {
            if (state.parameters[i] == pname) {
                state.parameters[i + 1] = value;
                return;
            }
        }
        if (state.parameterCount < state.parameters.length) {
            state.parameters[state.parameterCount++] = pname;
            state.parameters[state.parameterCount++] = value;
        }
    }

    void texImage2D(
            int level, int internalFormat, int width, int height, int format, int type, @Nullable ByteBuffer pixels) {
        TextureState state = boundTexture();
        if (state == null || level != 0) {
            return;
        }
        state.internalFormat = internalFormat;
        state.width = width;
        state.height = height;
        state.format = format;
        state.type = type;
        if (pixels == null) {
            state.data = null;
        } else {
            byte[] copy = new byte[pixels.remaining()];
            pixels.duplicate().get(copy);
            state.data = copy;
        }
    }

    void texSubImage2D(int level, int x, int y, int width, int height, int format, int type, ByteBuffer pixels) {
        TextureState state = boundTexture();
        int bytes = bytesPerPixel(format, type);
        if (state == null || level != 0 || bytes == 0 || bytes != bytesPerPixel(state.format, state.type)) {
            return;
        }
        int rowBytes = state.width * bytes;
        if (state.data == null) {
            state.data = new byte[rowBytes * state.height];
        }
        ByteBuffer source = pixels.duplicate();
        int sourceRow = width * bytes;
        int start = source.position();
        for (int row = 0; row < height && y + row < state.height; row++) {
            int copy = Math.min(sourceRow, (state.width - x) * bytes);
            if (copy <= 0) {
                break;
            }
            source.position(start + row * sourceRow);
            source.get(state.data, (y + row) * rowBytes + x * bytes, copy);
        }
    }

    void generateMipmap() {
        TextureState state = boundTexture();
        if (state != null) {
            state.mipmaps = true;
        }
    }

    static int bytesPerPixel(int format, int type) {
        if (type != Gl.UNSIGNED_BYTE) {
            return 0;
        }
        return format == Gl.RGBA ? 4 : format == Gl.RGB ? 3 : format == Gl.RG ? 2 : format == Gl.RED ? 1 : 0;
    }

    void bindFramebuffer(int target, int framebuffer) {
        if (target == Gl.FRAMEBUFFER || target == Gl.DRAW_FRAMEBUFFER) {
            drawFramebuffer = framebuffer;
        }
    }

    void framebufferAttachment(int attachment, int kind, int object) {
        List<int[]> attachments =
                drawFramebuffer > 0 && drawFramebuffer < framebuffers.length ? framebuffers[drawFramebuffer] : null;
        if (attachments == null) {
            return;
        }
        attachments.removeIf(existing -> existing[0] == attachment);
        attachments.add(new int[] {attachment, kind, object});
    }

    void bindRenderbuffer(int handle) {
        renderbuffer = handle;
    }

    void renderbufferStorage(int internalFormat, int width, int height) {
        int[] state = renderbuffer > 0 && renderbuffer < renderbuffers.length ? renderbuffers[renderbuffer] : null;
        if (state != null) {
            state[0] = internalFormat;
            state[1] = width;
            state[2] = height;
        }
    }

    /**
     * Returns how many bytes of pixel and buffer copies the journal keeps.
     *
     * @return the size
     */
    long shadowBytes() {
        long total = 0;
        for (BufferState buffer : buffers) {
            if (buffer != null && buffer.data != null) {
                total += buffer.data.length;
            }
        }
        for (TextureState texture : textures) {
            if (texture != null && texture.data != null) {
                total += texture.data.length;
            }
        }
        return total;
    }

    // ================================================================== replay

    /**
     * Rebuilds every object on a fresh context, with the recording turned off by the caller.
     *
     * @param gl the context, reached through the same handles
     * @param recreator makes new objects behind the handles
     */
    void replay(Gl gl, Recreator recreator) {
        gl.pixelStorei(Gl.UNPACK_ALIGNMENT, 1);
        for (int handle = 1; handle < shaders.length; handle++) {
            ShaderState shader = shaders[handle];
            if (shader != null) {
                recreator.recreate(Kind.SHADER, handle);
                gl.shaderSource(handle, shader.source);
                gl.compileShader(handle);
            }
        }
        for (int handle = 1; handle < programs.length; handle++) {
            ProgramState program = programs[handle];
            if (program == null) {
                continue;
            }
            recreator.recreate(Kind.PROGRAM, handle);
            List<Integer> temporary = new ArrayList<>();
            for (int i = 0; i + 1 < program.attributes.size(); i += 2) {
                int index = (Integer) program.attributes.get(i);
                String text = (String) program.attributes.get(i + 1);
                if (index >= 0) {
                    gl.bindAttribLocation(handle, index, text);
                } else {
                    int shader = gl.createShader(-1 - index);
                    gl.shaderSource(shader, text);
                    gl.compileShader(shader);
                    gl.attachShader(handle, shader);
                    temporary.add(shader);
                }
            }
            if (program.linked) {
                gl.linkProgram(handle);
            }
            for (int shader : temporary) {
                gl.detachShader(handle, shader);
                gl.deleteShader(shader);
            }
            for (int i = 0; i < program.blockNames.size(); i++) {
                int block = gl.getUniformBlockIndex(handle, program.blockNames.get(i));
                gl.uniformBlockBinding(handle, block, program.blockBindings.get(i)[0]);
            }
        }
        for (int handle = 1; handle < buffers.length; handle++) {
            BufferState buffer = buffers[handle];
            if (buffer == null) {
                continue;
            }
            recreator.recreate(Kind.BUFFER, handle);
            if (buffer.target != 0) {
                gl.bindBuffer(buffer.target, handle);
                byte[] data = buffer.data;
                if (data != null) {
                    gl.bufferData(buffer.target, direct(data), buffer.usage);
                } else {
                    gl.bufferData(buffer.target, buffer.size, buffer.usage);
                }
                gl.bindBuffer(buffer.target, 0);
            }
        }
        for (int[] base : bufferBases) {
            gl.bindBufferBase(base[0], base[1], base[2]);
        }
        gl.activeTexture(Gl.TEXTURE0);
        for (int handle = 1; handle < textures.length; handle++) {
            TextureState texture = textures[handle];
            if (texture == null) {
                continue;
            }
            recreator.recreate(Kind.TEXTURE, handle);
            gl.bindTexture(Gl.TEXTURE_2D, handle);
            if (texture.width > 0) {
                byte[] data = texture.data;
                gl.texImage2D(
                        Gl.TEXTURE_2D,
                        0,
                        texture.internalFormat,
                        texture.width,
                        texture.height,
                        texture.format,
                        texture.type,
                        data == null ? null : direct(data));
            }
            for (int i = 0; i < texture.parameterCount; i += 2) {
                gl.texParameteri(Gl.TEXTURE_2D, texture.parameters[i], texture.parameters[i + 1]);
            }
            if (texture.mipmaps) {
                gl.generateMipmap(Gl.TEXTURE_2D);
            }
        }
        gl.bindTexture(Gl.TEXTURE_2D, 0);
        for (int handle = 1; handle < renderbuffers.length; handle++) {
            int[] storage = renderbuffers[handle];
            if (storage == null) {
                continue;
            }
            recreator.recreate(Kind.RENDERBUFFER, handle);
            if (storage[1] > 0) {
                gl.bindRenderbuffer(Gl.RENDERBUFFER, handle);
                gl.renderbufferStorage(Gl.RENDERBUFFER, storage[0], storage[1], storage[2]);
            }
        }
        gl.bindRenderbuffer(Gl.RENDERBUFFER, 0);
        for (int handle = 1; handle < framebuffers.length; handle++) {
            List<int[]> attachments = framebuffers[handle];
            if (attachments == null) {
                continue;
            }
            recreator.recreate(Kind.FRAMEBUFFER, handle);
            gl.bindFramebuffer(Gl.FRAMEBUFFER, handle);
            for (int[] attachment : attachments) {
                if (attachment[1] == 0) {
                    gl.framebufferTexture2D(Gl.FRAMEBUFFER, attachment[0], Gl.TEXTURE_2D, attachment[2], 0);
                } else {
                    gl.framebufferRenderbuffer(Gl.FRAMEBUFFER, attachment[0], Gl.RENDERBUFFER, attachment[2]);
                }
            }
        }
        gl.bindFramebuffer(Gl.FRAMEBUFFER, 0);
        for (int handle = 1; handle < vertexArrays.length; handle++) {
            VertexArrayState layout = vertexArrays[handle];
            if (layout == null) {
                continue;
            }
            recreator.recreate(Kind.VERTEX_ARRAY, handle);
            gl.bindVertexArray(handle);
            for (int[] op : layout.operations) {
                switch (op[0]) {
                    case ENABLE -> gl.enableVertexAttribArray(op[1]);
                    case DISABLE -> gl.disableVertexAttribArray(op[1]);
                    case POINTER -> {
                        gl.bindBuffer(Gl.ARRAY_BUFFER, op[7]);
                        gl.vertexAttribPointer(op[1], op[2], op[3], op[4] != 0, op[5], op[6]);
                    }
                    case DIVISOR -> gl.vertexAttribDivisor(op[1], op[2]);
                    default -> gl.bindBuffer(Gl.ELEMENT_ARRAY_BUFFER, op[1]);
                }
            }
        }
        gl.bindVertexArray(0);
        gl.bindBuffer(Gl.ARRAY_BUFFER, 0);
        for (int location = 1; location < uniforms.length; location++) {
            UniformState uniform = uniforms[location];
            if (uniform == null) {
                continue;
            }
            gl.useProgram(uniform.program);
            int fresh = gl.getUniformLocation(uniform.program, uniform.name);
            relocate(location, fresh);
            if (fresh <= 0) {
                continue;
            }
            float[] v = uniform.values;
            switch (uniform.kind) {
                case 1 -> gl.uniform1f(location, v[0]);
                case 2 -> gl.uniform2f(location, v[0], v[1]);
                case 3 -> gl.uniform3f(location, v[0], v[1], v[2]);
                case 4 -> gl.uniform4f(location, v[0], v[1], v[2], v[3]);
                case 5 -> gl.uniform1i(location, uniform.intValue);
                case 6 -> gl.uniform1fv(location, direct(v, uniform.count));
                case 9 -> gl.uniform4fv(location, direct(v, uniform.count));
                case 7 -> gl.uniformMatrix3fv(location, direct(v, uniform.count));
                case 8 -> gl.uniformMatrix4fv(location, direct(v, uniform.count));
                default -> {}
            }
        }
        gl.useProgram(0);
        arrayBuffer = 0;
        elementBuffer = 0;
        uniformBuffer = 0;
        vertexArray = 0;
        activeUnit = 0;
        Arrays.fill(unitTextures, 0);
        drawFramebuffer = 0;
        renderbuffer = 0;
    }

    /** WebGL under Wasm GC reads only buffers in linear memory, so replayed data is copied into direct buffers. */
    private static ByteBuffer direct(byte[] data) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(data.length).order(java.nio.ByteOrder.nativeOrder());
        buffer.put(data).flip();
        return buffer;
    }

    private static FloatBuffer direct(float[] values, int count) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(count * 4)
                .order(java.nio.ByteOrder.nativeOrder())
                .asFloatBuffer();
        buffer.put(values, 0, count).flip();
        return buffer;
    }

    /** Called during replay when a uniform location was fetched again; the context moves it to the old handle. */
    private @Nullable Relocator relocator;

    /** Moves a freshly fetched uniform location object to the handle the engine already has. */
    interface Relocator {
        /**
         * Moves the object of one handle to another.
         *
         * @param handle the engine's handle
         * @param fresh the handle of the new location
         */
        void relocate(int handle, int fresh);
    }

    void setRelocator(@Nullable Relocator value) {
        this.relocator = value;
    }

    private void relocate(int handle, int fresh) {
        Relocator current = relocator;
        if (current != null && fresh > 0 && fresh != handle) {
            current.relocate(handle, fresh);
        }
    }
}
