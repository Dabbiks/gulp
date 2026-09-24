package dev.gulp.api.input;

import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.graphics.TextureRegion;
import org.jspecify.annotations.Nullable;

/**
 * The mouse pointer image: a system shape ({@link SystemCursor}) or a custom image.
 *
 * <pre>{@code
 * input().setCursor(SystemCursor.HAND);
 * input().setCursor(Cursor.custom(assets().region("coins:sprites/ui/pointer"), 0, 0));
 * }</pre>
 */
public sealed interface Cursor permits SystemCursor, Cursor.Custom {

    /**
     * Returns a cursor made from an atlas or texture region.
     *
     * @param region the image, at most 128 by 128 pixels
     * @param hotX horizontal position of the click point in the image
     * @param hotY vertical position of the click point in the image
     * @return the cursor
     */
    static Cursor custom(TextureRegion region, int hotX, int hotY) {
        return new Custom(null, region, hotX, hotY);
    }

    /**
     * Returns a cursor made from an image in memory.
     *
     * @param image the image, at most 128 by 128 pixels
     * @param hotX horizontal position of the click point
     * @param hotY vertical position of the click point
     * @return the cursor
     */
    static Cursor custom(Pixmap image, int hotX, int hotY) {
        return new Custom(image, null, hotX, hotY);
    }

    /**
     * A custom cursor image; exactly one of the image sources is set.
     *
     * <pre>{@code
     * Cursor sword = Cursor.custom(swordPixmap, 2, 2);
     * }</pre>
     *
     * @param pixmap the image in memory, or {@code null}
     * @param region the texture region, or {@code null}
     * @param hotX horizontal click point
     * @param hotY vertical click point
     */
    record Custom(@Nullable Pixmap pixmap, @Nullable TextureRegion region, int hotX, int hotY) implements Cursor {}
}
