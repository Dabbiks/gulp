package dev.gulp.core.graphics;

import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.graphics.TextureFilter;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.graphics.TextureWrap;
import dev.gulp.platform.Gl;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** RGBA8 texture with premultiplied alpha. */
public final class TextureImpl implements Texture {

    private final Gl gl;
    private final int handle;
    private final int width;
    private final int height;
    private TextureFilter filter;
    private TextureWrap wrap = TextureWrap.CLAMP;
    private boolean disposed;
    private final TextureRegion whole;

    TextureImpl(Gl gl, int width, int height, TextureFilter filter, @org.jspecify.annotations.Nullable Pixmap pixels) {
        this.gl = gl;
        this.width = width;
        this.height = height;
        this.filter = filter;
        this.handle = gl.createTexture();
        gl.bindTexture(Gl.TEXTURE_2D, handle);
        gl.pixelStorei(Gl.UNPACK_ALIGNMENT, 1);
        gl.texImage2D(
                Gl.TEXTURE_2D,
                0,
                Gl.RGBA8,
                width,
                height,
                Gl.RGBA,
                Gl.UNSIGNED_BYTE,
                pixels == null ? null : premultiplied(pixels));
        applyParameters();
        this.whole = new TextureRegion(this, 0, 0, width, height);
    }

    /**
     * Converts straight RGBA to premultiplied RGBA bytes.
     *
     * @param pixmap the source
     * @return a direct buffer ready for upload
     */
    static ByteBuffer premultiplied(Pixmap pixmap) {
        byte[] rgba = pixmap.toRgba();
        ByteBuffer buffer = ByteBuffer.allocateDirect(rgba.length).order(ByteOrder.nativeOrder());
        for (int i = 0; i < rgba.length; i += 4) {
            int a = rgba[i + 3] & 0xff;
            buffer.put((byte) ((rgba[i] & 0xff) * a / 255));
            buffer.put((byte) ((rgba[i + 1] & 0xff) * a / 255));
            buffer.put((byte) ((rgba[i + 2] & 0xff) * a / 255));
            buffer.put((byte) a);
        }
        return buffer.flip();
    }

    private void applyParameters() {
        gl.bindTexture(Gl.TEXTURE_2D, handle);
        int min;
        int mag;
        switch (filter) {
            case NEAREST -> {
                min = Gl.NEAREST;
                mag = Gl.NEAREST;
            }
            case LINEAR -> {
                min = Gl.LINEAR;
                mag = Gl.LINEAR;
            }
            case MIPMAP_NEAREST -> {
                min = Gl.NEAREST_MIPMAP_NEAREST;
                mag = Gl.NEAREST;
            }
            default -> {
                min = Gl.LINEAR_MIPMAP_LINEAR;
                mag = Gl.LINEAR;
            }
        }
        if (filter == TextureFilter.MIPMAP_NEAREST || filter == TextureFilter.MIPMAP_LINEAR) {
            gl.generateMipmap(Gl.TEXTURE_2D);
        }
        gl.texParameteri(Gl.TEXTURE_2D, Gl.TEXTURE_MIN_FILTER, min);
        gl.texParameteri(Gl.TEXTURE_2D, Gl.TEXTURE_MAG_FILTER, mag);
        int mode =
                switch (wrap) {
                    case CLAMP -> Gl.CLAMP_TO_EDGE;
                    case REPEAT -> Gl.REPEAT;
                    case MIRROR -> Gl.MIRRORED_REPEAT;
                };
        gl.texParameteri(Gl.TEXTURE_2D, Gl.TEXTURE_WRAP_S, mode);
        gl.texParameteri(Gl.TEXTURE_2D, Gl.TEXTURE_WRAP_T, mode);
    }

    int handle() {
        return handle;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public TextureFilter filter() {
        return filter;
    }

    @Override
    public void setFilter(TextureFilter filter) {
        this.filter = filter;
        applyParameters();
    }

    @Override
    public TextureWrap wrap() {
        return wrap;
    }

    @Override
    public void setWrap(TextureWrap wrap) {
        this.wrap = wrap;
        applyParameters();
    }

    @Override
    public void update(Pixmap pixmap, int x, int y) {
        if (x < 0 || y < 0 || x + pixmap.width() > width || y + pixmap.height() > height) {
            throw new IllegalArgumentException("Pixmap " + pixmap.width() + "x" + pixmap.height() + " at " + x + "," + y
                    + " does not fit a " + width + "x" + height + " texture");
        }
        gl.bindTexture(Gl.TEXTURE_2D, handle);
        gl.pixelStorei(Gl.UNPACK_ALIGNMENT, 1);
        gl.texSubImage2D(
                Gl.TEXTURE_2D,
                0,
                x,
                y,
                pixmap.width(),
                pixmap.height(),
                Gl.RGBA,
                Gl.UNSIGNED_BYTE,
                premultiplied(pixmap));
        if (filter == TextureFilter.MIPMAP_NEAREST || filter == TextureFilter.MIPMAP_LINEAR) {
            gl.generateMipmap(Gl.TEXTURE_2D);
        }
    }

    @Override
    public TextureRegion region() {
        return whole;
    }

    @Override
    public TextureRegion region(int x, int y, int width, int height) {
        return new TextureRegion(this, x, y, width, height);
    }

    @Override
    public void dispose() {
        if (!disposed) {
            disposed = true;
            gl.deleteTexture(handle);
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
