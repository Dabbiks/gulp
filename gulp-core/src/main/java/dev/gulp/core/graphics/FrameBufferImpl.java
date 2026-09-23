package dev.gulp.core.graphics;

import dev.gulp.api.graphics.FrameBuffer;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.graphics.TextureFilter;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.platform.Gl;

/** Frame buffer with an RGBA8 color texture and an optional depth-stencil renderbuffer. */
public final class FrameBufferImpl implements FrameBuffer {

    private final Gl gl;
    private final int handle;
    private final int renderbuffer;
    private final TextureImpl texture;
    private final TextureRegion region;
    private boolean disposed;

    FrameBufferImpl(Gl gl, int width, int height, boolean stencil, TextureFilter filter) {
        this.gl = gl;
        this.texture = new TextureImpl(gl, width, height, filter, null);
        this.handle = gl.createFramebuffer();
        gl.bindFramebuffer(Gl.FRAMEBUFFER, handle);
        gl.framebufferTexture2D(Gl.FRAMEBUFFER, Gl.COLOR_ATTACHMENT0, Gl.TEXTURE_2D, texture.handle(), 0);
        if (stencil) {
            renderbuffer = gl.createRenderbuffer();
            gl.bindRenderbuffer(Gl.RENDERBUFFER, renderbuffer);
            gl.renderbufferStorage(Gl.RENDERBUFFER, Gl.DEPTH24_STENCIL8, width, height);
            gl.framebufferRenderbuffer(Gl.FRAMEBUFFER, Gl.DEPTH_STENCIL_ATTACHMENT, Gl.RENDERBUFFER, renderbuffer);
        } else {
            renderbuffer = 0;
        }
        int status = gl.checkFramebufferStatus(Gl.FRAMEBUFFER);
        gl.bindFramebuffer(Gl.FRAMEBUFFER, 0);
        if (status != Gl.FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Frame buffer is incomplete, status 0x" + Integer.toHexString(status));
        }
        // GL stores the top row last, so the upright image is the vertically flipped region.
        this.region = new TextureRegion(texture, 0, 0, width, height).flipY();
    }

    int handle() {
        return handle;
    }

    @Override
    public int width() {
        return texture.width();
    }

    @Override
    public int height() {
        return texture.height();
    }

    @Override
    public Texture texture() {
        return texture;
    }

    @Override
    public TextureRegion region() {
        return region;
    }

    @Override
    public boolean hasStencil() {
        return renderbuffer != 0;
    }

    @Override
    public void dispose() {
        if (!disposed) {
            disposed = true;
            gl.deleteFramebuffer(handle);
            gl.deleteRenderbuffer(renderbuffer);
            texture.dispose();
        }
    }

    boolean isDisposed() {
        return disposed;
    }
}
