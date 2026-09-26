package dev.gulp.backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gulp.platform.Gl;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The WebGL restore journal replays every object behind its old handle (section 20.3). */
class GlJournalTest {

    private final List<String> calls = new ArrayList<>();
    private int nextShader = 100;
    private int nextLocation = 200;

    /** A GL that only writes down what it is asked to do. */
    private Gl recordingGl() {
        return (Gl) Proxy.newProxyInstance(
                Gl.class.getClassLoader(), new Class<?>[] {Gl.class}, (proxy, method, args) -> {
                    StringBuilder call = new StringBuilder(method.getName());
                    if (args != null) {
                        for (Object arg : args) {
                            call.append(' ').append(describe(arg));
                        }
                    }
                    calls.add(call.toString());
                    return switch (method.getName()) {
                        case "createShader" -> nextShader++;
                        case "getUniformLocation" -> nextLocation++;
                        case "getUniformBlockIndex" -> 3;
                        default -> method.getReturnType() == int.class ? 0 : null;
                    };
                });
    }

    private static String describe(Object arg) {
        if (arg instanceof ByteBuffer buffer) {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.duplicate().get(bytes);
            return Arrays.toString(bytes);
        }
        if (arg instanceof FloatBuffer buffer) {
            float[] values = new float[buffer.remaining()];
            buffer.duplicate().get(values);
            return Arrays.toString(values);
        }
        return String.valueOf(arg);
    }

    @Test
    void replaysTexturesBuffersProgramsAndLayouts() {
        GlJournal journal = new GlJournal();
        // A 2x1 RGBA texture, later patched by a sub-image, with parameters and mipmaps.
        journal.created(GlJournal.Kind.TEXTURE, 1);
        journal.activeTexture(Gl.TEXTURE0);
        journal.bindTexture(1);
        journal.texImage2D(0, Gl.RGBA8, 2, 1, Gl.RGBA, Gl.UNSIGNED_BYTE, ByteBuffer.wrap(new byte[8]));
        journal.texSubImage2D(0, 1, 0, 1, 1, Gl.RGBA, Gl.UNSIGNED_BYTE, ByteBuffer.wrap(new byte[] {9, 9, 9, 9}));
        journal.texParameteri(Gl.TEXTURE_MIN_FILTER, Gl.NEAREST);
        journal.texParameteri(Gl.TEXTURE_MIN_FILTER, Gl.LINEAR);
        journal.generateMipmap();
        // A render target and a deleted texture.
        journal.created(GlJournal.Kind.TEXTURE, 2);
        journal.bindTexture(2);
        journal.texImage2D(0, Gl.RGBA8, 4, 4, Gl.RGBA, Gl.UNSIGNED_BYTE, null);
        journal.created(GlJournal.Kind.TEXTURE, 3);
        journal.deleted(GlJournal.Kind.TEXTURE, 3);
        // A static index buffer and a streamed vertex buffer.
        journal.created(GlJournal.Kind.BUFFER, 1);
        journal.created(GlJournal.Kind.BUFFER, 2);
        journal.created(GlJournal.Kind.VERTEX_ARRAY, 1);
        journal.bindVertexArray(1);
        journal.bindBuffer(Gl.ARRAY_BUFFER, 2);
        journal.bufferData(Gl.ARRAY_BUFFER, null, 64, Gl.DYNAMIC_DRAW);
        journal.vertexAttribPointer(0, 2, Gl.FLOAT, false, 16, 0);
        journal.enableVertexAttribArray(0);
        journal.bindBuffer(Gl.ELEMENT_ARRAY_BUFFER, 1);
        journal.bufferData(Gl.ELEMENT_ARRAY_BUFFER, ByteBuffer.wrap(new byte[] {0, 1, 2}), 0, Gl.STATIC_DRAW);
        journal.bindVertexArray(0);
        journal.bindBufferBase(Gl.UNIFORM_BUFFER, 0, 2);
        // A program whose shaders are deleted after linking, with a sampler and a matrix uniform.
        journal.shaderCreated(5, Gl.VERTEX_SHADER);
        journal.shaderSource(5, "vertex source");
        journal.shaderCreated(6, Gl.FRAGMENT_SHADER);
        journal.shaderSource(6, "fragment source");
        journal.created(GlJournal.Kind.PROGRAM, 1);
        journal.attachShader(1, 5);
        journal.attachShader(1, 6);
        journal.bindAttribLocation(1, 0, "a_position");
        journal.linkProgram(1);
        journal.uniformBlockBinding(1, "Lights", 2);
        journal.detachShader(1, 5);
        journal.deleted(GlJournal.Kind.SHADER, 5);
        journal.deleted(GlJournal.Kind.SHADER, 6);
        journal.uniformLocation(7, 1, "u_texture");
        journal.uniformInt(7, 0);
        journal.uniformLocation(8, 1, "u_projection");
        journal.uniformArray(8, 8, FloatBuffer.wrap(new float[] {1, 0, 0, 1}));
        journal.uniformLocation(9, 1, "u_tint");
        journal.uniformFloats(9, 4, 1f, 0.5f, 0.25f, 1f);
        // A frame buffer drawing into the render target, with a stencil render buffer.
        journal.created(GlJournal.Kind.RENDERBUFFER, 1);
        journal.bindRenderbuffer(1);
        journal.renderbufferStorage(Gl.DEPTH24_STENCIL8, 4, 4);
        journal.created(GlJournal.Kind.FRAMEBUFFER, 1);
        journal.bindFramebuffer(Gl.FRAMEBUFFER, 1);
        journal.framebufferAttachment(Gl.COLOR_ATTACHMENT0, 0, 2);
        journal.framebufferAttachment(Gl.DEPTH_STENCIL_ATTACHMENT, 1, 1);
        assertThat(journal.shadowBytes()).isEqualTo(8 + 3);

        List<String> recreated = new ArrayList<>();
        List<String> moved = new ArrayList<>();
        journal.setRelocator((handle, fresh) -> moved.add(fresh + "->" + handle));
        journal.replay(recordingGl(), (kind, handle) -> recreated.add(kind + " " + handle));

        assertThat(recreated)
                .containsExactly(
                        "PROGRAM 1",
                        "BUFFER 1",
                        "BUFFER 2",
                        "TEXTURE 1",
                        "TEXTURE 2",
                        "RENDERBUFFER 1",
                        "FRAMEBUFFER 1",
                        "VERTEX_ARRAY 1");
        assertThat(calls)
                .contains(
                        "shaderSource 100 vertex source",
                        "shaderSource 101 fragment source",
                        "bindAttribLocation 1 0 a_position",
                        "linkProgram 1",
                        "uniformBlockBinding 1 3 2",
                        "deleteShader 100",
                        "bufferData " + Gl.ELEMENT_ARRAY_BUFFER + " [0, 1, 2] " + Gl.STATIC_DRAW,
                        "bufferData " + Gl.ARRAY_BUFFER + " 64 " + Gl.DYNAMIC_DRAW,
                        "bindBufferBase " + Gl.UNIFORM_BUFFER + " 0 2",
                        "texImage2D " + Gl.TEXTURE_2D + " 0 " + Gl.RGBA8 + " 2 1 " + Gl.RGBA + " " + Gl.UNSIGNED_BYTE
                                + " [0, 0, 0, 0, 9, 9, 9, 9]",
                        "texImage2D " + Gl.TEXTURE_2D + " 0 " + Gl.RGBA8 + " 4 4 " + Gl.RGBA + " " + Gl.UNSIGNED_BYTE
                                + " null",
                        "texParameteri " + Gl.TEXTURE_2D + " " + Gl.TEXTURE_MIN_FILTER + " " + Gl.LINEAR,
                        "generateMipmap " + Gl.TEXTURE_2D,
                        "renderbufferStorage " + Gl.RENDERBUFFER + " " + Gl.DEPTH24_STENCIL8 + " 4 4",
                        "framebufferTexture2D " + Gl.FRAMEBUFFER + " " + Gl.COLOR_ATTACHMENT0 + " " + Gl.TEXTURE_2D
                                + " 2 0",
                        "framebufferRenderbuffer " + Gl.FRAMEBUFFER + " " + Gl.DEPTH_STENCIL_ATTACHMENT + " "
                                + Gl.RENDERBUFFER + " 1",
                        "vertexAttribPointer 0 2 " + Gl.FLOAT + " false 16 0",
                        "enableVertexAttribArray 0",
                        "bindBuffer " + Gl.ELEMENT_ARRAY_BUFFER + " 1",
                        "uniform1i 7 0",
                        "uniformMatrix4fv 8 [1.0, 0.0, 0.0, 1.0]",
                        "uniform4f 9 1.0 0.5 0.25 1.0");
        assertThat(calls)
                .doesNotContain("texParameteri " + Gl.TEXTURE_2D + " " + Gl.TEXTURE_MIN_FILTER + " " + Gl.NEAREST);
        assertThat(moved).containsExactly("200->7", "201->8", "202->9");
    }

    @Test
    void onlyUnsignedBytePixelsAreKept() {
        assertThat(GlJournal.bytesPerPixel(Gl.RGBA, Gl.UNSIGNED_BYTE)).isEqualTo(4);
        assertThat(GlJournal.bytesPerPixel(Gl.RGB, Gl.UNSIGNED_BYTE)).isEqualTo(3);
        assertThat(GlJournal.bytesPerPixel(Gl.RG, Gl.UNSIGNED_BYTE)).isEqualTo(2);
        assertThat(GlJournal.bytesPerPixel(Gl.RED, Gl.UNSIGNED_BYTE)).isEqualTo(1);
        assertThat(GlJournal.bytesPerPixel(Gl.RGBA, Gl.FLOAT)).isZero();
    }
}
