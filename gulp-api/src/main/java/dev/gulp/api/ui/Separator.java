package dev.gulp.api.ui;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.render.Draw;

/**
 * A thin line between items: horizontal in a column, vertical in a row (it follows its longer side). Created by
 * {@link Ui#separator}. Theme type: {@code separator} (track colour).
 *
 * <pre>{@code
 * column(button("New game"), button("Continue"), separator(), button("Quit"));
 * }</pre>
 */
public final class Separator extends Node<Separator> {

    /** Creates a separator. */
    public Separator() {}

    @Override
    protected String styleType() {
        return "separator";
    }

    @Override
    protected boolean usesPadding() {
        return false;
    }

    @Override
    protected Size measure() {
        return new Size(1f, 1f);
    }

    @Override
    protected void draw(Draw draw) {
        Color previous = draw.color();
        draw.color(style().track());
        if (width >= height) {
            draw.rect(x, y + height / 2f - 0.5f, width, 1f);
        } else {
            draw.rect(x + width / 2f - 0.5f, y, 1f, height);
        }
        draw.color(previous);
    }
}
