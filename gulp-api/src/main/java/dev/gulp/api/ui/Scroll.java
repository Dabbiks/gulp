package dev.gulp.api.ui;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.input.MouseButton;
import dev.gulp.api.render.Draw;

/**
 * Shows part of a larger child and scrolls it: with the wheel, by dragging the content or the bar (mouse and touch),
 * with the right stick, and automatically to keep the focused node visible. By default it scrolls vertically, shows
 * the bar only when needed, and grows along its container's axis, taking the free space in a column. Created by
 * {@link Ui#scroll}. Theme type: {@code scroll} (track colour for the bar, accent for the thumb).
 *
 * <pre>{@code
 * column(label("Log").variant("heading"), scroll(column(lines)).grow());
 * scroll(wideMap).horizontal(ScrollPolicy.AUTO).vertical(ScrollPolicy.AUTO).size(300, 200);
 * }</pre>
 */
public final class Scroll extends Container<Scroll> {

    /** When a scroll bar shows and whether the axis scrolls at all. */
    public enum ScrollPolicy {
        /** Scrolls; the bar shows when the content is larger. */
        AUTO,
        /** Scrolls; the bar always shows. */
        ALWAYS,
        /** Does not scroll; the content fits this axis. */
        NEVER
    }

    private static final float BAR = 6f;
    private static final float MIN_VIEW = 48f;

    private ScrollPolicy horizontal = ScrollPolicy.NEVER;
    private ScrollPolicy vertical = ScrollPolicy.AUTO;
    private float scrollX;
    private float scrollY;
    private float contentWidth;
    private float contentHeight;
    private boolean draggingBar;
    private boolean draggingContent;
    private float lastX;
    private float lastY;

    /**
     * Creates a scroll container.
     *
     * @param child the content
     */
    public Scroll(Node<?> child) {
        super(child);
        grow();
    }

    @Override
    protected String styleType() {
        return "scroll";
    }

    @Override
    protected MouseFilter defaultMouseFilter() {
        return MouseFilter.PASS;
    }

    /**
     * Sets the horizontal policy.
     *
     * @param policy the policy; {@code NEVER} by default
     * @return this scroll
     */
    public Scroll horizontal(ScrollPolicy policy) {
        this.horizontal = policy;
        invalidate();
        return this;
    }

    /**
     * Sets the vertical policy.
     *
     * @param policy the policy; {@code AUTO} by default
     * @return this scroll
     */
    public Scroll vertical(ScrollPolicy policy) {
        this.vertical = policy;
        invalidate();
        return this;
    }

    /**
     * Returns the horizontal scroll position.
     *
     * @return UI points from the left of the content
     */
    public float scrollX() {
        return scrollX;
    }

    /**
     * Returns the vertical scroll position.
     *
     * @return UI points from the top of the content
     */
    public float scrollY() {
        return scrollY;
    }

    /**
     * Returns how far the content can scroll horizontally.
     *
     * @return UI points, 0 if it fits
     */
    public float maxScrollX() {
        return horizontal == ScrollPolicy.NEVER ? 0f : Math.max(0f, contentWidth - width);
    }

    /**
     * Returns how far the content can scroll vertically.
     *
     * @return UI points, 0 if it fits
     */
    public float maxScrollY() {
        return vertical == ScrollPolicy.NEVER ? 0f : Math.max(0f, contentHeight - height);
    }

    /**
     * Scrolls to a position, clamped to the content.
     *
     * @param sx horizontal position
     * @param sy vertical position
     * @return this scroll
     */
    public Scroll scrollTo(float sx, float sy) {
        float nx = Math.max(0f, Math.min(sx, maxScrollX()));
        float ny = Math.max(0f, Math.min(sy, maxScrollY()));
        if (nx != scrollX || ny != scrollY) {
            scrollX = nx;
            scrollY = ny;
            dirty = true;
            invalidate();
        }
        return this;
    }

    /**
     * Scrolls by an amount.
     *
     * @param dx horizontal UI points
     * @param dy vertical UI points
     * @return {@code true} if the position changed
     */
    public boolean scrollBy(float dx, float dy) {
        float oldX = scrollX;
        float oldY = scrollY;
        scrollTo(scrollX + dx, scrollY + dy);
        return oldX != scrollX || oldY != scrollY;
    }

    /**
     * Scrolls just enough to show a descendant.
     *
     * @param node a node inside the content
     * @return this scroll
     */
    public Scroll scrollTo(Node<?> node) {
        float nx = scrollX;
        float ny = scrollY;
        if (node.x() < x) {
            nx -= x - node.x();
        } else if (node.x() + node.width() > x + width) {
            nx += Math.min(node.x() - x, node.x() + node.width() - x - width);
        }
        if (node.y() < y) {
            ny -= y - node.y();
        } else if (node.y() + node.height() > y + height) {
            ny += Math.min(node.y() - y, node.y() + node.height() - y - height);
        }
        return scrollTo(nx, ny);
    }

    /**
     * Returns whether the content is larger than the view along an axis.
     *
     * @param horizontalAxis whether to ask about the X axis
     * @return {@code true} if it can scroll that way
     */
    public boolean canScroll(boolean horizontalAxis) {
        return horizontalAxis ? maxScrollX() > 0f : maxScrollY() > 0f;
    }

    @Override
    public boolean clipsChildren() {
        return true;
    }

    @Override
    protected Size measure() {
        float w = maxMin(true);
        float h = maxMin(false);
        if (horizontal != ScrollPolicy.NEVER) {
            w = Math.min(w, MIN_VIEW);
        }
        if (vertical != ScrollPolicy.NEVER) {
            h = Math.min(h, MIN_VIEW);
        }
        return new Size(w, h);
    }

    @Override
    protected void arrange() {
        contentWidth = horizontal == ScrollPolicy.NEVER ? width : Math.max(width, maxMin(true));
        contentHeight = vertical == ScrollPolicy.NEVER ? height : Math.max(height, maxMin(false));
        scrollX = Math.max(0f, Math.min(scrollX, maxScrollX()));
        scrollY = Math.max(0f, Math.min(scrollY, maxScrollY()));
        for (int i = 0; i < children.size(); i++) {
            Node<?> child = children.get(i);
            if (child.isVisible()) {
                place(child, x - scrollX, y - scrollY, contentWidth, contentHeight);
            }
        }
    }

    @Override
    protected boolean scrolled(float dx, float dy) {
        float step = 40f;
        if (vertical == ScrollPolicy.NEVER) {
            return scrollBy((dx + dy) * step, 0f);
        }
        return scrollBy(dx * step, dy * step);
    }

    @Override
    protected boolean pointerDown(float px, float py, MouseButton button) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        lastX = px;
        lastY = py;
        draggingBar = maxScrollY() > 0f && px >= x + width - BAR * 2f;
        draggingContent = !draggingBar && (maxScrollX() > 0f || maxScrollY() > 0f);
        return draggingBar || draggingContent;
    }

    @Override
    protected void pointerDrag(float px, float py) {
        if (draggingBar) {
            float thumb = Math.max(24f, height * height / Math.max(contentHeight, 1f));
            float perPoint = maxScrollY() / Math.max(1f, height - thumb);
            scrollBy(0f, (py - lastY) * perPoint);
        } else if (draggingContent) {
            scrollBy(lastX - px, lastY - py);
        }
        lastX = px;
        lastY = py;
    }

    @Override
    protected void pointerUp(float px, float py, boolean inside) {
        draggingBar = false;
        draggingContent = false;
    }

    /**
     * Starts scrolling by dragging from a point, taking over a press that began on a child.
     *
     * @param px UI x of the pointer
     * @param py UI y of the pointer
     */
    public void beginDrag(float px, float py) {
        lastX = px;
        lastY = py;
        draggingContent = true;
        draggingBar = false;
    }

    @Override
    protected void drawOver(Draw draw) {
        Style style = style();
        Color previous = draw.color();
        boolean showY = vertical == ScrollPolicy.ALWAYS || (vertical == ScrollPolicy.AUTO && maxScrollY() > 0f);
        boolean showX = horizontal == ScrollPolicy.ALWAYS || (horizontal == ScrollPolicy.AUTO && maxScrollX() > 0f);
        if (showY) {
            float thumb = Math.max(24f, height * height / Math.max(contentHeight, 1f));
            float max = maxScrollY();
            float ty = y + (max <= 0f ? 0f : (height - thumb) * scrollY / max);
            draw.color(style.track()).roundedRect(x + width - BAR - 2f, y + 2f, BAR, height - 4f, BAR / 2f);
            draw.color(style.accent()).roundedRect(x + width - BAR - 2f, ty + 2f, BAR, thumb - 4f, BAR / 2f);
        }
        if (showX) {
            float thumb = Math.max(24f, width * width / Math.max(contentWidth, 1f));
            float max = maxScrollX();
            float tx = x + (max <= 0f ? 0f : (width - thumb) * scrollX / max);
            draw.color(style.track()).roundedRect(x + 2f, y + height - BAR - 2f, width - 4f, BAR, BAR / 2f);
            draw.color(style.accent()).roundedRect(tx + 2f, y + height - BAR - 2f, thumb - 4f, BAR, BAR / 2f);
        }
        draw.color(previous);
    }
}
