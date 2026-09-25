package dev.gulp.api.ui;

/**
 * An empty node that expands, pushing its siblings apart. Created by {@link Ui#spacer}. Theme type: {@code spacer}.
 *
 * <pre>{@code
 * row(label("Score"), spacer(), label(score.map(String::valueOf)));
 * }</pre>
 */
public final class Spacer extends Node<Spacer> {

    /** Creates a spacer that expands with ratio 1. */
    public Spacer() {
        expand(1f);
    }

    @Override
    protected String styleType() {
        return "spacer";
    }

    @Override
    protected boolean usesPadding() {
        return false;
    }
}
