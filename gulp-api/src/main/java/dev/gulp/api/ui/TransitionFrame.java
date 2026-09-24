package dev.gulp.api.ui;

import dev.gulp.api.graphics.TextureRegion;
import org.jspecify.annotations.Nullable;

/**
 * The state of a transition for one frame.
 *
 * <pre>{@code
 * draw.color(Color.BLACK.withAlpha(frame.coverage())).rect(0, 0, frame.width(), frame.height());
 * }</pre>
 *
 * @param width logical screen width
 * @param height logical screen height
 * @param coverage how much the old or new picture is hidden: rises from 0 to 1 while covering, falls back to 0
 * @param entering {@code false} while covering the old world, {@code true} while uncovering the new one
 * @param screen the rendered picture, when {@link Transition#needsFrame()}; otherwise {@code null}
 */
public record TransitionFrame(
        float width,
        float height,
        float coverage,
        boolean entering,
        @Nullable TextureRegion screen) {}
