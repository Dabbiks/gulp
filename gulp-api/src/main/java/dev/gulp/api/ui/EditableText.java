package dev.gulp.api.ui;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.input.Input;
import dev.gulp.api.input.KeyboardKey;
import dev.gulp.api.input.Keys;
import dev.gulp.api.input.MouseButton;
import dev.gulp.api.input.SystemCursor;
import dev.gulp.api.math.Rect;
import dev.gulp.api.render.Draw;
import dev.gulp.api.spi.UiAccess;
import dev.gulp.api.text.Text;
import dev.gulp.api.text.TextBox;
import dev.gulp.api.text.TextLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Base of {@link TextField} and {@link TextArea}: editing with a caret and selection, clipboard, character filter,
 * validation, placeholder, password masking, and scrolling to the caret. While focused it takes the keyboard (the game
 * does not see the keys) and starts text input, so IME and on-screen keyboards work.
 *
 * <pre>{@code
 * textField().placeholder("Name").maxLength(16).validate(s -> !s.isBlank()).onSubmit(this::rename);
 * }</pre>
 *
 * @param <N> the widget's own type
 */
@SuppressWarnings("this-escape")
public abstract class EditableText<N extends EditableText<N>> extends Node<N> {

    private static final int SHIFT = 1;
    private static final int CONTROL = 2 | 8;

    private final boolean multiline;
    private final StringBuilder text = new StringBuilder();
    private int caret;
    private int anchor = -1;
    private String placeholder = "";
    private boolean password;
    private int maxLength = Integer.MAX_VALUE;
    private @Nullable IntPredicate filter;
    private @Nullable Predicate<String> validator;
    private boolean invalid;
    private String baseVariant = "";
    private float blink;
    private float scrollX;
    private float scrollY;
    private final List<String> lines = new ArrayList<>();
    private final List<@Nullable TextLayout> lineLayouts = new ArrayList<>();
    private boolean linesValid;
    private @Nullable TextLayout placeholderLayout;
    private int caretFor = -1;
    private float caretX;
    private int caretLine;
    private int selectionFrom = -1;
    private int selectionTo = -1;
    private float[] selection = new float[0];
    private int selectionCount;
    private @Nullable Style metricsStyle;
    private float lineHeight;
    private @Nullable Rect clip;
    private final Runnable drawContent = this::drawContent;
    private @Nullable Draw drawTarget;

    EditableText(boolean multiline) {
        this.multiline = multiline;
        cursor(SystemCursor.TEXT);
    }

    @Override
    protected String styleType() {
        return "field";
    }

    @Override
    protected boolean isFocusableByDefault() {
        return true;
    }

    @Override
    protected MouseFilter defaultMouseFilter() {
        return MouseFilter.STOP;
    }

    // ------------------------------------------------------------------ configuration

    /**
     * Sets the text shown while empty.
     *
     * @param value the placeholder
     * @return this widget
     */
    public N placeholder(String value) {
        this.placeholder = value;
        return self();
    }

    /**
     * Shows dots instead of the characters.
     *
     * @param value whether to mask
     * @return this widget
     */
    public N password(boolean value) {
        this.password = value;
        linesValid = false;
        return self();
    }

    /**
     * Limits the length.
     *
     * @param value the most characters
     * @return this widget
     */
    public N maxLength(int value) {
        this.maxLength = Math.max(0, value);
        return self();
    }

    /**
     * Accepts only characters that pass a test, such as digits: {@code filter(Character::isDigit)}.
     *
     * @param test the character test
     * @return this widget
     */
    public N filter(IntPredicate test) {
        this.filter = test;
        return self();
    }

    /**
     * Marks the text invalid (the theme's {@code invalid} variant) while a test fails.
     *
     * @param test the text test
     * @return this widget
     */
    public N validate(Predicate<String> test) {
        this.validator = test;
        revalidate();
        return self();
    }

    /**
     * Returns whether the text passes the validation.
     *
     * @return {@code true} if valid or without validation
     */
    public boolean isValid() {
        return !invalid;
    }

    /**
     * Replaces the text without firing a change; the caret moves to the end.
     *
     * @param value the text
     * @return this widget
     */
    public N text(String value) {
        String clean = multiline ? value : value.replace("\n", " ");
        if (!clean.contentEquals(text)) {
            text.setLength(0);
            text.append(clean, 0, Math.min(clean.length(), maxLength));
            caret = text.length();
            anchor = -1;
            changedContent(false);
        }
        return self();
    }

    /**
     * Returns the text.
     *
     * @return the current text
     */
    public String text() {
        return text.toString();
    }

    /**
     * Keeps the text in a state, both ways.
     *
     * @param state the text
     * @return this widget
     */
    public N bind(State<String> state) {
        bind(state, this::text);
        onChange(state::set);
        return self();
    }

    /**
     * Runs an action after every edit.
     *
     * @param action receives the new text
     * @return this widget
     */
    public N onChange(Consumer<String> action) {
        return on(NodeEvent.Change.class, e -> action.accept((String) e.value()));
    }

    /**
     * Runs an action when Enter is pressed (Control+Enter in a text area).
     *
     * @param action receives the text
     * @return this widget
     */
    public N onSubmit(Consumer<String> action) {
        return on(NodeEvent.Submit.class, e -> action.accept(e.text()));
    }

    /**
     * Returns the caret position.
     *
     * @return characters before the caret
     */
    public int caret() {
        return caret;
    }

    /**
     * Returns the selected text.
     *
     * @return the selection, empty if none
     */
    public String selectedText() {
        if (anchor < 0 || anchor == caret) {
            return "";
        }
        return text.substring(Math.min(anchor, caret), Math.max(anchor, caret));
    }

    // ------------------------------------------------------------------ editing

    private void changedContent(boolean user) {
        linesValid = false;
        blink = 0f;
        revalidate();
        if (user) {
            fireChange(text.toString());
        }
    }

    private void revalidate() {
        Predicate<String> test = validator;
        boolean nowInvalid = test != null && !test.test(text.toString());
        if (nowInvalid != invalid) {
            if (nowInvalid) {
                baseVariant = variant();
                invalid = true;
                variant("invalid");
            } else {
                invalid = false;
                variant(baseVariant);
            }
        }
    }

    private boolean deleteSelection() {
        if (anchor < 0 || anchor == caret) {
            anchor = -1;
            return false;
        }
        int from = Math.min(anchor, caret);
        int to = Math.max(anchor, caret);
        text.delete(from, to);
        caret = from;
        anchor = -1;
        return true;
    }

    /**
     * Inserts text at the caret, replacing the selection, as if typed.
     *
     * @param inserted the text
     */
    public void insert(String inserted) {
        StringBuilder accepted = new StringBuilder();
        IntPredicate test = filter;
        for (int i = 0; i < inserted.length(); ) {
            int cp = inserted.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\r' || (cp == '\n' && !multiline) || (test != null && cp != '\n' && !test.test(cp))) {
                continue;
            }
            accepted.appendCodePoint(cp);
        }
        boolean removed = deleteSelection();
        int room = maxLength - text.length();
        if (accepted.length() > room) {
            accepted.setLength(Math.max(0, room));
        }
        if (accepted.length() == 0 && !removed) {
            return;
        }
        text.insert(caret, accepted);
        caret += accepted.length();
        changedContent(true);
    }

    private void moveCaret(int to, boolean select) {
        int clamped = Math.max(0, Math.min(text.length(), to));
        if (select) {
            if (anchor < 0) {
                anchor = caret;
            }
        } else {
            anchor = -1;
        }
        caret = clamped;
        blink = 0f;
    }

    private int lineOf(int index) {
        int line = 0;
        for (int i = 0; i < index && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private int lineStart(int index) {
        int i = Math.min(index, text.length());
        while (i > 0 && text.charAt(i - 1) != '\n') {
            i--;
        }
        return i;
    }

    private int lineEnd(int index) {
        int i = index;
        while (i < text.length() && text.charAt(i) != '\n') {
            i++;
        }
        return i;
    }

    private int wordLeft(int from) {
        int i = from;
        while (i > 0 && Character.isWhitespace(text.charAt(i - 1))) {
            i--;
        }
        while (i > 0 && !Character.isWhitespace(text.charAt(i - 1))) {
            i--;
        }
        return i;
    }

    private int wordRight(int from) {
        int i = from;
        while (i < text.length() && !Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private void verticalMove(int lines, boolean select) {
        int start = lineStart(caret);
        float column = measure(text.substring(start, caret));
        int target = caret;
        if (lines < 0) {
            if (start == 0) {
                moveCaret(0, select);
                return;
            }
            target = lineStart(start - 1);
        } else {
            int end = lineEnd(caret);
            if (end >= text.length()) {
                moveCaret(text.length(), select);
                return;
            }
            target = end + 1;
        }
        moveCaret(indexAtX(target, lineEnd(target), column), select);
    }

    private int indexAtX(int from, int to, float column) {
        int best = from;
        float bestDistance = Float.MAX_VALUE;
        for (int i = from; i <= to; i++) {
            float distance = Math.abs(measure(text.substring(from, i)) - column);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    @Override
    protected boolean keyPressed(KeyboardKey key, int modifiers, boolean repeat) {
        boolean shift = (modifiers & SHIFT) != 0;
        boolean control = (modifiers & CONTROL) != 0;
        if (key == Keys.LEFT) {
            moveCaret(control ? wordLeft(caret) : (anchor >= 0 && !shift ? Math.min(anchor, caret) : caret - 1), shift);
        } else if (key == Keys.RIGHT) {
            moveCaret(
                    control ? wordRight(caret) : (anchor >= 0 && !shift ? Math.max(anchor, caret) : caret + 1), shift);
        } else if (key == Keys.HOME) {
            moveCaret(control || !multiline ? 0 : lineStart(caret), shift);
        } else if (key == Keys.END) {
            moveCaret(control || !multiline ? text.length() : lineEnd(caret), shift);
        } else if (key == Keys.UP && multiline) {
            verticalMove(-1, shift);
        } else if (key == Keys.DOWN && multiline) {
            verticalMove(1, shift);
        } else if (key == Keys.BACKSPACE) {
            if (!deleteSelection() && caret > 0) {
                int from = control ? wordLeft(caret) : caret - 1;
                text.delete(from, caret);
                caret = from;
            }
            changedContent(true);
        } else if (key == Keys.DELETE) {
            if (!deleteSelection() && caret < text.length()) {
                text.delete(caret, control ? wordRight(caret) : caret + 1);
            }
            changedContent(true);
        } else if (key == Keys.ENTER || key == Keys.KP_ENTER) {
            if (multiline && !control) {
                insert("\n");
            } else {
                fireSubmit(text.toString());
            }
        } else if (control && key == Keys.A) {
            anchor = 0;
            caret = text.length();
        } else if (control && (key == Keys.C || key == Keys.X)) {
            String selected = selectedText();
            if (!selected.isEmpty() && !password) {
                UiAccess.backend().input().clipboard().set(selected);
                if (key == Keys.X) {
                    deleteSelection();
                    changedContent(true);
                }
            }
        } else if (control && key == Keys.V) {
            Input input = UiAccess.backend().input();
            input.clipboard().get().thenSync(this::insert);
        } else if (key == Keys.ESCAPE || key == Keys.TAB || (!multiline && (key == Keys.UP || key == Keys.DOWN))) {
            return false;
        }
        return true;
    }

    @Override
    protected boolean charTyped(int codePoint) {
        if (codePoint >= 32 && codePoint != 127) {
            insert(new String(Character.toChars(codePoint)));
        }
        return true;
    }

    @Override
    protected boolean navigate(UiAction action) {
        // Keys arrive through keyPressed; the actions they also trigger must not move the focus away.
        return action == UiAction.ACCEPT
                || action == UiAction.LEFT
                || action == UiAction.RIGHT
                || (multiline && (action == UiAction.UP || action == UiAction.DOWN));
    }

    @Override
    protected void focusChanged(boolean gained) {
        if (!UiAccess.hasBackend()) {
            return;
        }
        if (gained) {
            caret = text.length();
            anchor = -1;
            blink = 0f;
            UiAccess.backend().startTextInput(bounds());
        } else {
            anchor = -1;
            UiAccess.backend().stopTextInput();
        }
    }

    @Override
    protected boolean pointerDown(float px, float py, MouseButton button) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        requestFocus();
        moveCaret(indexAt(px, py), false);
        anchor = caret;
        return true;
    }

    @Override
    protected void pointerDrag(float px, float py) {
        int index = indexAt(px, py);
        if (anchor < 0) {
            anchor = caret;
        }
        caret = index;
    }

    @Override
    protected void pointerUp(float px, float py, boolean inside) {
        if (anchor == caret) {
            anchor = -1;
        }
    }

    private int indexAt(float px, float py) {
        metrics();
        Insets pad = padding();
        int line = multiline
                ? Math.max(0, Math.min(lines.size() - 1, (int) ((py - y - pad.top() + scrollY) / lineHeight)))
                : 0;
        int start = 0;
        for (int i = 0; i < line; i++) {
            start = lineEnd(start) + 1;
        }
        return indexAtX(start, lineEnd(start), px - x - pad.left() + scrollX);
    }

    // ------------------------------------------------------------------ measuring and drawing

    private String shown(String raw) {
        if (!password) {
            return raw;
        }
        StringBuilder dots = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            dots.append('•');
        }
        return dots.toString();
    }

    private float measure(String raw) {
        if (raw.isEmpty()) {
            return 0f;
        }
        return layoutText(Text.of(shown(raw)), style().textStyle(), TextBox.NONE)
                .width();
    }

    private void metrics() {
        Style style = style();
        if (metricsStyle != style) {
            metricsStyle = style;
            lineHeight =
                    layoutText(Text.of("Ag"), style.textStyle(), TextBox.NONE).height();
            placeholderLayout = null;
            linesValid = false;
        }
        if (!linesValid) {
            lines.clear();
            lineLayouts.clear();
            int start = 0;
            while (true) {
                int end = lineEnd(start);
                String line = text.substring(start, end);
                lines.add(line);
                lineLayouts.add(
                        line.isEmpty() ? null : layoutText(Text.of(shown(line)), style.textStyle(), TextBox.NONE));
                if (end >= text.length()) {
                    break;
                }
                start = end + 1;
            }
            linesValid = true;
            caretFor = -1;
            selectionFrom = -1;
        }
    }

    /**
     * Returns how many lines tall the widget is by default.
     *
     * @return 1 for a field
     */
    int rows() {
        return 1;
    }

    @Override
    protected Size measure() {
        metrics();
        return new Size(0f, lineHeight * rows());
    }

    @Override
    protected void update(float seconds) {
        blink += seconds;
    }

    private void keepCaretVisible(float innerW, float innerH) {
        if (caretX - scrollX > innerW - 2f) {
            scrollX = caretX - innerW + 2f;
        } else if (caretX < scrollX) {
            scrollX = Math.max(0f, caretX - innerW / 3f);
        }
        if (multiline) {
            float caretY = caretLine * lineHeight;
            if (caretY + lineHeight - scrollY > innerH) {
                scrollY = caretY + lineHeight - innerH;
            } else if (caretY < scrollY) {
                scrollY = caretY;
            }
        }
    }

    @Override
    protected void draw(Draw draw) {
        drawBackground(draw);
        Insets pad = padding();
        Rect area = clip;
        if (area == null
                || area.x() != x + pad.left()
                || area.y() != y + pad.top()
                || area.width() != width - pad.horizontal()) {
            area = new Rect(
                    x + pad.left(),
                    y + pad.top(),
                    Math.max(0f, width - pad.horizontal()),
                    Math.max(0f, height - pad.vertical()));
            clip = area;
        }
        drawTarget = draw;
        draw.clip(area, drawContent);
        drawTarget = null;
    }

    private void refreshCaret() {
        if (caretFor != caret) {
            int start = lineStart(caret);
            caretX = measure(text.substring(start, caret));
            caretLine = lineOf(caret);
            caretFor = caret;
        }
    }

    private void refreshSelection() {
        int from = anchor < 0 ? caret : Math.min(anchor, caret);
        int to = anchor < 0 ? caret : Math.max(anchor, caret);
        if (from == selectionFrom && to == selectionTo) {
            return;
        }
        selectionFrom = from;
        selectionTo = to;
        selectionCount = 0;
        if (from == to) {
            return;
        }
        if (selection.length < lines.size() * 3) {
            selection = new float[lines.size() * 3];
        }
        int lineStart = 0;
        for (int line = 0; line < lines.size(); line++) {
            int lineEnd = lineStart + lines.get(line).length();
            int a = Math.max(from, lineStart);
            int b = Math.min(to, lineEnd);
            if (a < b || (from <= lineEnd && to > lineEnd)) {
                float sx = measure(text.substring(lineStart, Math.min(a, lineEnd)));
                float ex = measure(text.substring(lineStart, Math.max(a, b))) + (to > lineEnd ? 4f : 0f);
                selection[selectionCount * 3] = sx;
                selection[selectionCount * 3 + 1] = line;
                selection[selectionCount * 3 + 2] = Math.max(1f, ex - sx);
                selectionCount++;
            }
            lineStart = lineEnd + 1;
        }
    }

    private void drawContent() {
        Draw draw = drawTarget;
        if (draw == null) {
            return;
        }
        metrics();
        refreshCaret();
        Style style = style();
        Insets pad = padding();
        float innerW = width - pad.horizontal();
        float innerH = height - pad.vertical();
        if (focused) {
            keepCaretVisible(innerW, innerH);
        }
        float left = x + pad.left() - scrollX;
        float top = y + pad.top() - (multiline ? scrollY : (lineHeight - innerH) / 2f);
        Color previous = draw.color();
        if (text.length() == 0 && !placeholder.isEmpty()) {
            TextLayout hint = placeholderLayout;
            if (hint == null) {
                hint = layoutText(Text.of(placeholder), style.textStyle().color(style.mutedColor()), TextBox.NONE);
                placeholderLayout = hint;
            }
            draw.text(hint, left, top);
        }
        if (focused) {
            refreshSelection();
            if (selectionCount > 0) {
                draw.color(style.accent().withAlpha(0.35f));
                for (int i = 0; i < selectionCount; i++) {
                    draw.rect(
                            left + selection[i * 3],
                            top + selection[i * 3 + 1] * lineHeight,
                            selection[i * 3 + 2],
                            lineHeight);
                }
                draw.color(previous);
            }
        }
        for (int line = 0; line < lineLayouts.size(); line++) {
            TextLayout layout = lineLayouts.get(line);
            if (layout != null) {
                draw.text(layout, left, top + line * lineHeight);
            }
        }
        if (focused && (blink % 1f) < 0.55f) {
            draw.color(style.textColor()).rect(left + caretX, top + caretLine * lineHeight + 1f, 1.5f, lineHeight - 2f);
            draw.color(previous);
        }
    }
}
