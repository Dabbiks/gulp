package dev.gulp.backend.headless;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gulp.platform.Gl;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;

class HeadlessGlTest {

    private final HeadlessGl gl = HeadlessRunnerTest.backend().gl();

    @Test
    void recordsClearState() {
        gl.viewport(1, 2, 3, 4);
        gl.clearColor(0.1f, 0.2f, 0.3f, 0.4f);
        gl.clear(Gl.COLOR_BUFFER_BIT);

        assertThat(gl.viewport()).containsExactly(1, 2, 3, 4);
        assertThat(gl.lastClearColor()).containsExactly(0.1f, 0.2f, 0.3f, 0.4f);
        assertThat(gl.clearCount()).isEqualTo(1);
        assertThat(gl.callCount()).isEqualTo(3);
    }

    @Test
    void handlesAreUniqueAndQueriesSucceed() {
        int shader = gl.createShader(Gl.VERTEX_SHADER);
        int program = gl.createProgram();

        assertThat(shader).isPositive().isNotEqualTo(program);
        assertThat(gl.getShaderi(shader, Gl.COMPILE_STATUS)).isEqualTo(Gl.TRUE);
        assertThat(gl.getShaderi(shader, 0)).isZero();
        assertThat(gl.getProgrami(program, Gl.LINK_STATUS)).isEqualTo(Gl.TRUE);
        assertThat(gl.getProgrami(program, 0)).isZero();
        assertThat(gl.getShaderInfoLog(shader)).isEmpty();
        assertThat(gl.getProgramInfoLog(program)).isEmpty();
        assertThat(gl.checkFramebufferStatus(Gl.FRAMEBUFFER)).isEqualTo(Gl.FRAMEBUFFER_COMPLETE);
        assertThat(gl.getError()).isEqualTo(Gl.NO_ERROR);
        assertThat(gl.getInteger(Gl.MAX_TEXTURE_SIZE)).isEqualTo(8192);
        assertThat(gl.getInteger(0)).isZero();
        assertThat(gl.getString(Gl.VENDOR)).isEqualTo("Gulp");
        assertThat(gl.getString(Gl.RENDERER)).isEqualTo("Headless");
        assertThat(gl.getString(Gl.VERSION)).contains("ES 3.0");
        assertThat(gl.getString(0)).isNull();
    }

    @Test
    void everyCallIsCountedAndDrawsAreCountedSeparately() {
        ByteBuffer bytes = ByteBuffer.allocateDirect(16);
        FloatBuffer floats = FloatBuffer.allocate(16);

        gl.enable(Gl.BLEND);
        gl.disable(Gl.BLEND);
        gl.scissor(0, 0, 1, 1);
        gl.colorMask(true, true, true, true);
        gl.blendFunc(Gl.SRC_ALPHA, Gl.ONE_MINUS_SRC_ALPHA);
        gl.blendFuncSeparate(Gl.ONE, Gl.ZERO, Gl.ONE, Gl.ZERO);
        gl.blendEquation(Gl.FUNC_ADD);
        gl.blendEquationSeparate(Gl.FUNC_ADD, Gl.MAX);
        gl.pixelStorei(Gl.UNPACK_ALIGNMENT, 1);
        int buffer = gl.createBuffer();
        gl.bindBuffer(Gl.ARRAY_BUFFER, buffer);
        gl.bufferData(Gl.ARRAY_BUFFER, bytes, Gl.STATIC_DRAW);
        gl.bufferData(Gl.ARRAY_BUFFER, 64, Gl.STREAM_DRAW);
        gl.bufferSubData(Gl.ARRAY_BUFFER, 0, bytes);
        gl.bindBufferBase(Gl.UNIFORM_BUFFER, 0, buffer);
        gl.deleteBuffer(buffer);
        int vao = gl.createVertexArray();
        gl.bindVertexArray(vao);
        gl.enableVertexAttribArray(0);
        gl.vertexAttribPointer(0, 2, Gl.FLOAT, false, 8, 0);
        gl.vertexAttribDivisor(0, 1);
        gl.disableVertexAttribArray(0);
        gl.deleteVertexArray(vao);
        int shader = gl.createShader(Gl.FRAGMENT_SHADER);
        gl.shaderSource(shader, "void main() {}");
        gl.compileShader(shader);
        int program = gl.createProgram();
        gl.attachShader(program, shader);
        gl.bindAttribLocation(program, 0, "a_position");
        gl.linkProgram(program);
        gl.detachShader(program, shader);
        gl.deleteShader(shader);
        gl.useProgram(program);
        int location = gl.getUniformLocation(program, "u_color");
        int block = gl.getUniformBlockIndex(program, "Camera");
        gl.uniformBlockBinding(program, block, 0);
        gl.uniform1i(location, 0);
        gl.uniform1f(location, 1f);
        gl.uniform2f(location, 1f, 2f);
        gl.uniform3f(location, 1f, 2f, 3f);
        gl.uniform4f(location, 1f, 2f, 3f, 4f);
        gl.uniform1fv(location, floats);
        gl.uniform4fv(location, floats);
        gl.uniformMatrix3fv(location, floats);
        gl.uniformMatrix4fv(location, floats);
        gl.deleteProgram(program);
        int texture = gl.createTexture();
        gl.activeTexture(Gl.TEXTURE0);
        gl.bindTexture(Gl.TEXTURE_2D, texture);
        gl.texParameteri(Gl.TEXTURE_2D, Gl.TEXTURE_MIN_FILTER, Gl.NEAREST);
        gl.texImage2D(Gl.TEXTURE_2D, 0, Gl.RGBA8, 2, 2, Gl.RGBA, Gl.UNSIGNED_BYTE, null);
        gl.texSubImage2D(Gl.TEXTURE_2D, 0, 0, 0, 2, 2, Gl.RGBA, Gl.UNSIGNED_BYTE, bytes);
        gl.generateMipmap(Gl.TEXTURE_2D);
        int framebuffer = gl.createFramebuffer();
        gl.bindFramebuffer(Gl.FRAMEBUFFER, framebuffer);
        gl.framebufferTexture2D(Gl.FRAMEBUFFER, Gl.COLOR_ATTACHMENT0, Gl.TEXTURE_2D, texture, 0);
        int renderbuffer = gl.createRenderbuffer();
        gl.bindRenderbuffer(Gl.RENDERBUFFER, renderbuffer);
        gl.renderbufferStorage(Gl.RENDERBUFFER, Gl.DEPTH24_STENCIL8, 2, 2);
        gl.framebufferRenderbuffer(Gl.FRAMEBUFFER, Gl.DEPTH_STENCIL_ATTACHMENT, Gl.RENDERBUFFER, renderbuffer);
        gl.blitFramebuffer(0, 0, 2, 2, 0, 0, 2, 2, Gl.NEAREST);
        gl.readPixels(0, 0, 2, 2, Gl.RGBA, Gl.UNSIGNED_BYTE, bytes);
        gl.deleteRenderbuffer(renderbuffer);
        gl.deleteFramebuffer(framebuffer);
        gl.deleteTexture(texture);
        gl.drawArrays(Gl.TRIANGLES, 0, 3);
        gl.drawElements(Gl.TRIANGLES, 6, Gl.UNSIGNED_SHORT, 0);
        gl.drawArraysInstanced(Gl.TRIANGLES, 0, 3, 10);
        gl.drawElementsInstanced(Gl.TRIANGLES, 6, Gl.UNSIGNED_SHORT, 0, 10);

        assertThat(gl.callCount()).isEqualTo(69);
        assertThat(gl.drawCallCount()).isEqualTo(4);
    }
}
