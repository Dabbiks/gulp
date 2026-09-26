package dev.gulp.api.save;

import dev.gulp.api.graphics.Pixmap;
import org.jspecify.annotations.Nullable;

/**
 * What a slot list shows about a save, read without loading it.
 *
 * <pre>{@code
 * saves().slot("slot1").metadata().thenSync(meta ->
 *         label(meta.title() + " — " + meta.playTicks() / 3600 + " min"));
 * }</pre>
 *
 * @param slot the slot name
 * @param title the title given to {@link SaveSlot#save(String)}, or the slot name
 * @param savedAt when it was saved, milliseconds since 1970 (UTC)
 * @param playTicks game ticks played in total
 * @param version the save version it was written with
 * @param thumbnail a small screenshot, or {@code null}
 */
public record SaveMetadata(
        String slot,
        String title,
        long savedAt,
        long playTicks,
        int version,
        @Nullable Pixmap thumbnail) {}
