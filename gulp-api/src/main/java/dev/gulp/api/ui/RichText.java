package dev.gulp.api.ui;

import dev.gulp.api.Gulp;
import dev.gulp.api.input.MouseButton;
import dev.gulp.api.render.Draw;
import dev.gulp.api.text.Markup;
import dev.gulp.api.text.TextLayout;
import dev.gulp.api.text.TextLinkClickEvent;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Formatted, wrapping text from markup ({@code [b]}, {@code [color=gold]}, {@code [img=...]}, {@code [link=shop]},
 * animated {@code [wave]}, {@code [shake]}, {@code [rainbow]} and more, see {@link Markup}). Clicking a link fires
 * {@link TextLinkClickEvent} and the node's {@link #onLink} handlers. {@link #reveal(Observable)} shows the text
 * character by character for a typewriter effect. Created by {@link Ui#richText}. Theme type: {@code label}.
 *
 * <pre>{@code
 * richText("Press [color=gold]E[/color] to [wave]open[/wave] the [link=shop]shop[/link].")
 *         .onLink(id -> ui().push(new ShopScreen()));
 * }</pre>
 */
public final class RichText extends Label {

    private @Nullable Consumer<String> linkHandler;
    private int visible = -1;
    private @Nullable String pressedLink;

    /**
     * Creates rich text from markup.
     *
     * @param markup the markup
     */
    public RichText(String markup) {
        super(Markup.parse(markup));
        wrap(true);
    }

    @Override
    protected MouseFilter defaultMouseFilter() {
        return MouseFilter.PASS;
    }

    /**
     * Runs an action when a link is clicked.
     *
     * @param action receives the link id
     * @return this node
     */
    public RichText onLink(Consumer<String> action) {
        this.linkHandler = action;
        mouseFilter(MouseFilter.STOP);
        return this;
    }

    /**
     * Shows only the first characters, for a typewriter effect; tween the observable from 0 to the length.
     *
     * @param count how many characters show
     * @return this node
     */
    public RichText reveal(Observable<Integer> count) {
        bind(count, value -> visible = value);
        return this;
    }

    /**
     * Returns the number of characters, for {@link #reveal}.
     *
     * @return the character count of the layout
     */
    public int characterCount() {
        return layout().characterCount();
    }

    private @Nullable String linkAt(float px, float py) {
        TextLayout current = layout();
        Insets pad = padding();
        return current.linkAt(px - x - pad.left(), py - y - pad.top());
    }

    @Override
    protected boolean pointerDown(float px, float py, MouseButton button) {
        pressedLink = button == MouseButton.LEFT ? linkAt(px, py) : null;
        return pressedLink != null || super.pointerDown(px, py, button);
    }

    @Override
    protected void pointerUp(float px, float py, boolean inside) {
        String link = pressedLink;
        pressedLink = null;
        if (link != null && link.equals(linkAt(px, py))) {
            Consumer<String> handler = linkHandler;
            if (handler != null) {
                handler.accept(link);
            }
            Gulp.engine().events().call(new TextLinkClickEvent(link));
            return;
        }
        super.pointerUp(px, py, inside);
    }

    @Override
    protected void drawText(Draw draw, TextLayout current, float tx, float ty) {
        if (visible >= 0) {
            draw.text(current, tx, ty, visible);
        } else {
            draw.text(current, tx, ty);
        }
    }
}
