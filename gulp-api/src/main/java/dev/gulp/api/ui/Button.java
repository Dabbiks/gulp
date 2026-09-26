package dev.gulp.api.ui;

import dev.gulp.api.Gulp;
import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.asset.Assets;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.render.Draw;
import dev.gulp.api.text.Text;
import dev.gulp.api.text.TextBox;
import dev.gulp.api.text.TextLayout;
import org.jspecify.annotations.Nullable;

/**
 * A button with text, an icon or both, pressed with the mouse, touch, Enter or the gamepad's accept button. Created by
 * {@link Ui#button} and {@link Ui#iconButton}. Theme type: {@code button}; variants {@code primary}, {@code danger},
 * {@code flat}.
 *
 * <pre>{@code
 * button(tr("menu.play")).variant("primary").onClick(() -> worlds().switchTo("level1", Transitions.fade(0.4f)));
 * button("Buy", GameAssets.Sprites.COIN).enabled(canAfford);
 * iconButton(GameAssets.Sprites.GEAR).tooltip("Settings").onClick(() -> ui().push(new SettingsScreen()));
 * }</pre>
 */
public final class Button extends Node<Button> {

    private Text text;
    private @Nullable TextureRegion icon;
    private @Nullable AssetKey<TextureRegion> iconKey;
    private @Nullable TextLayout layout;
    private @Nullable Style layoutStyle;
    private int layoutRevision;

    /**
     * Creates a text button.
     *
     * @param label the text
     */
    public Button(String label) {
        this.text = Text.of(label);
    }

    /**
     * Creates a button whose text follows a value.
     *
     * @param label the text
     */
    public Button(Observable<String> label) {
        this.text = Text.empty();
        bind(label, this::text);
    }

    /**
     * Creates a button with an icon before the text.
     *
     * @param label the text, empty for an icon button
     * @param icon the icon region key
     */
    public Button(String label, AssetKey<TextureRegion> icon) {
        this.text = Text.of(label);
        this.iconKey = icon;
    }

    /**
     * Creates a button with an icon before the text.
     *
     * @param label the text, empty for an icon button
     * @param icon the icon region
     */
    public Button(String label, TextureRegion icon) {
        this.text = Text.of(label);
        this.icon = icon;
    }

    @Override
    protected String styleType() {
        return "button";
    }

    @Override
    protected boolean isFocusableByDefault() {
        return true;
    }

    @Override
    protected MouseFilter defaultMouseFilter() {
        return MouseFilter.STOP;
    }

    @Override
    protected boolean isClickable() {
        return true;
    }

    /**
     * Changes the text.
     *
     * @param label the new text
     * @return this button
     */
    public Button text(String label) {
        Text next = Text.of(label);
        if (!next.equals(text)) {
            text = next;
            layout = null;
            invalidate();
        }
        return this;
    }

    /**
     * Returns the text.
     *
     * @return the plain text
     */
    public String text() {
        return text.plain();
    }

    @Override
    protected void mounted() {
        AssetKey<TextureRegion> wanted = iconKey;
        if (wanted != null && icon == null) {
            Assets assets = Gulp.engine().assets();
            if (assets.isLoaded(wanted)) {
                icon = assets.get(wanted);
            } else {
                assets.load(wanted).thenSync(loaded -> {
                    icon = loaded;
                    invalidate();
                });
            }
        }
    }

    private @Nullable TextLayout layout() {
        if (text.plain().isEmpty()) {
            return null;
        }
        Style style = style();
        TextLayout current = layout;
        if (current == null || layoutStyle != style || layoutRevision != textRevision()) {
            current = layoutText(text, style.textStyle(), TextBox.NONE);
            layout = current;
            layoutStyle = style;
            layoutRevision = textRevision();
        }
        return current;
    }

    private float iconSize() {
        TextureRegion current = icon;
        if (current == null) {
            return 0f;
        }
        return Math.min(current.height(), style().fontSize() * 1.25f);
    }

    @Override
    protected Size measure() {
        TextLayout current = layout();
        float iconSize = iconSize();
        float w = (current != null ? current.width() : 0f) + iconSize + (current != null && iconSize > 0f ? 6f : 0f);
        float h = Math.max(current != null ? current.height() : 0f, iconSize);
        return new Size(w, h);
    }

    @Override
    protected void draw(Draw draw) {
        drawBackground(draw);
        TextLayout current = layout();
        float iconSize = iconSize();
        float contentWidth =
                (current != null ? current.width() : 0f) + iconSize + (current != null && iconSize > 0f ? 6f : 0f);
        float cx = x + (width - contentWidth) / 2f;
        TextureRegion image = icon;
        if (image != null && iconSize > 0f) {
            float w = image.width() * iconSize / Math.max(1f, image.height());
            Color previous = draw.color();
            draw.color(isEnabled() ? tint() : tint().withAlpha(0.5f));
            draw.image(image, cx, y + (height - iconSize) / 2f, w, iconSize);
            draw.color(previous);
            cx += w + 6f;
        }
        if (current != null) {
            draw.text(current, cx, y + (height - current.height()) / 2f);
        }
    }
}
