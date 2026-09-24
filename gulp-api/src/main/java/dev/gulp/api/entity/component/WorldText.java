package dev.gulp.api.entity.component;

import dev.gulp.api.entity.Component;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.text.Text;
import dev.gulp.api.text.TextStyle;

/**
 * Text shown above the entity, such as a name or damage number. It is drawn in screen space at a constant size after the
 * world layers, centred on a point above the entity, and hidden with the entity.
 *
 * <pre>{@code
 * npc.add(new WorldText(Text.of("Kowal").color(Color.GOLD)).style(TextStyle.of(14).outline(2, Color.BLACK)));
 * }</pre>
 */
public final class WorldText extends Component {

    private Text text;
    private TextStyle style = TextStyle.of(14);
    private Vec2 offset = new Vec2(0f, -0.25f);

    /**
     * Creates the component.
     *
     * @param text the text
     */
    public WorldText(Text text) {
        this.text = text;
    }

    /**
     * Creates the component.
     *
     * @param text plain text
     */
    public WorldText(String text) {
        this(Text.of(text));
    }

    /**
     * Returns the text.
     *
     * @return the text
     */
    public Text text() {
        return text;
    }

    /**
     * Changes the text.
     *
     * @param value the text
     * @return this component
     */
    public WorldText text(Text value) {
        this.text = value;
        return this;
    }

    /**
     * Changes the text.
     *
     * @param value plain text
     * @return this component
     */
    public WorldText text(String value) {
        return text(Text.of(value));
    }

    /**
     * Returns the style.
     *
     * @return the style; sizes are in screen points
     */
    public TextStyle style() {
        return style;
    }

    /**
     * Changes the style.
     *
     * @param value the style
     * @return this component
     */
    public WorldText style(TextStyle value) {
        this.style = value;
        return this;
    }

    /**
     * Returns the offset from the top centre of the entity bounds.
     *
     * @return world units
     */
    public Vec2 offset() {
        return offset;
    }

    /**
     * Moves the text.
     *
     * @param x world units
     * @param y world units
     * @return this component
     */
    public WorldText offset(float x, float y) {
        this.offset = new Vec2(x, y);
        return this;
    }
}
