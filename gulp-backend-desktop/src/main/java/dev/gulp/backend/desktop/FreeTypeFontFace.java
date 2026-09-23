package dev.gulp.backend.desktop;

import static org.lwjgl.util.freetype.FreeType.*;

import dev.gulp.platform.GlyphBitmap;
import dev.gulp.platform.PlatformFontFace;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.freetype.FT_Vector;

/** A font file opened with FreeType; glyphs are rendered with light hinting as 8-bit coverage. */
final class FreeTypeFontFace implements PlatformFontFace {

    private final long library;
    private final FT_Face face;
    private final ByteBuffer data;
    private final float unitsPerEm;
    private int currentSize = -1;
    private boolean disposed;

    private FreeTypeFontFace(long library, FT_Face face, ByteBuffer data) {
        this.library = library;
        this.face = face;
        this.data = data;
        this.unitsPerEm = Math.max(1, face.units_per_EM() & 0xffff);
    }

    /**
     * Opens a font.
     *
     * @param fontFile TTF or OTF bytes
     * @return the face
     * @throws IllegalArgumentException if FreeType cannot read it
     */
    static FreeTypeFontFace open(ByteBuffer fontFile) {
        // FreeType reads from the buffer for the face's lifetime, so it keeps its own copy.
        ByteBuffer data = MemoryUtil.memAlloc(fontFile.remaining());
        data.put(fontFile.duplicate()).flip();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pointer = stack.mallocPointer(1);
            if (FT_Init_FreeType(pointer) != 0) {
                MemoryUtil.memFree(data);
                throw new IllegalStateException("FreeType could not start");
            }
            long library = pointer.get(0);
            if (FT_New_Memory_Face(library, data, 0, pointer) != 0) {
                FT_Done_FreeType(library);
                MemoryUtil.memFree(data);
                throw new IllegalArgumentException("Not a font file FreeType can read");
            }
            return new FreeTypeFontFace(library, FT_Face.create(pointer.get(0)), data);
        }
    }

    private void size(float sizePixels) {
        int size = Math.max(1, Math.round(sizePixels));
        if (size != currentSize) {
            FT_Set_Pixel_Sizes(face, 0, size);
            currentSize = size;
        }
    }

    @Override
    public int glyphIndex(int codePoint) {
        return FT_Get_Char_Index(face, codePoint);
    }

    @Override
    public GlyphBitmap rasterize(int glyphIndex, float sizePixels) {
        size(sizePixels);
        if (FT_Load_Glyph(face, glyphIndex, FT_LOAD_RENDER | FT_LOAD_TARGET_LIGHT) != 0) {
            return new GlyphBitmap(0, 0, 0f, 0f, 0f, ByteBuffer.allocateDirect(0));
        }
        FT_GlyphSlot slot = face.glyph();
        if (slot == null) {
            return new GlyphBitmap(0, 0, 0f, 0f, 0f, ByteBuffer.allocateDirect(0));
        }
        FT_Bitmap bitmap = slot.bitmap();
        int width = bitmap.width();
        int rows = bitmap.rows();
        int pitch = bitmap.pitch();
        ByteBuffer coverage = ByteBuffer.allocateDirect(width * rows).order(ByteOrder.nativeOrder());
        if (width > 0 && rows > 0) {
            ByteBuffer source = bitmap.buffer(Math.abs(pitch) * rows);
            if (source != null) {
                for (int y = 0; y < rows; y++) {
                    int row = pitch >= 0 ? y : rows - 1 - y;
                    for (int x = 0; x < width; x++) {
                        coverage.put(source.get(row * Math.abs(pitch) + x));
                    }
                }
            }
        }
        coverage.flip();
        float advance = slot.advance().x() / 64f;
        return new GlyphBitmap(width, rows, slot.bitmap_left(), slot.bitmap_top(), advance, coverage);
    }

    @Override
    public float kerning(int leftGlyph, int rightGlyph, float sizePixels) {
        size(sizePixels);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FT_Vector vector = FT_Vector.malloc(stack);
            if (FT_Get_Kerning(face, leftGlyph, rightGlyph, FT_KERNING_DEFAULT, vector) != 0) {
                return 0f;
            }
            return vector.x() / 64f;
        }
    }

    @Override
    public float ascent(float sizePixels) {
        return face.ascender() / unitsPerEm * sizePixels;
    }

    @Override
    public float descent(float sizePixels) {
        return -face.descender() / unitsPerEm * sizePixels;
    }

    @Override
    public float lineHeight(float sizePixels) {
        return face.height() / unitsPerEm * sizePixels;
    }

    @Override
    public void dispose() {
        if (!disposed) {
            disposed = true;
            FT_Done_Face(face);
            FT_Done_FreeType(library);
            MemoryUtil.memFree(data);
        }
    }
}
