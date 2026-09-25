package dev.gulp.core;

import static dev.gulp.api.ui.Ui.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.ui.Align;
import dev.gulp.api.ui.Anchor;
import dev.gulp.api.ui.Button;
import dev.gulp.api.ui.Column;
import dev.gulp.api.ui.Flow;
import dev.gulp.api.ui.Grid;
import dev.gulp.api.ui.Insets;
import dev.gulp.api.ui.Label;
import dev.gulp.api.ui.Node;
import dev.gulp.api.ui.Row;
import dev.gulp.api.ui.Scroll;
import dev.gulp.api.ui.Spacer;
import dev.gulp.api.ui.Split;
import dev.gulp.api.ui.Stack;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class UiLayoutTest extends UiFixture {

    private static final Offset<Float> NEAR = Offset.offset(1.01f);

    @Test
    void rowsAndColumnsShareFreeSpaceByRatio() {
        startUi();
        Node<?> a = spacer().expand(1f);
        Node<?> b = spacer().expand(3f);
        Node<?> fixed = spacer().expand(0f).size(100f, 20f);
        Row row = row(a, fixed, b).gap(10f);
        Column root = column(row.height(40f), spacer()).gap(0f);
        open(root);
        assertThat(root.width()).isEqualTo(1280f);
        assertThat(root.height()).isEqualTo(720f);
        float free = 1280f - 100f - 20f;
        assertThat(a.width()).isCloseTo(free / 4f, NEAR);
        assertThat(b.width()).isCloseTo(free * 3f / 4f, NEAR);
        assertThat(fixed.x()).isCloseTo(a.width() + 10f, NEAR);
        assertThat(fixed.height()).isEqualTo(20f);
        assertThat(a.height()).isEqualTo(40f);
        assertThat(row.isHorizontal()).isTrue();
        assertThat(row.gap()).isEqualTo(10f);

        Node<?> capped = spacer().expand(1f).maxSize(50f, Float.POSITIVE_INFINITY);
        Node<?> other = spacer().expand(1f);
        row.clear().add(capped, other);
        step(1);
        assertThat(capped.width()).isEqualTo(50f);
        assertThat(other.width()).isCloseTo(1280f - 50f - 10f, NEAR);
    }

    @Test
    void justifyAlignAndVisibility() {
        startUi();
        Button first = button("First");
        Button second = button("Second").alignY(Align.END);
        Row row = row(first, second).height(100f).justify(Align.CENTER).align(Align.START);
        Label hidden = label("hidden").visible(false);
        open(column(row, hidden).gap(0f));
        float used = first.width() + row.gap() + second.width();
        assertThat(first.x()).isCloseTo((1280f - used) / 2f, NEAR);
        assertThat(first.y()).isEqualTo(0f);
        assertThat(first.height()).isEqualTo(first.minHeight());
        assertThat(second.y() + second.height()).isCloseTo(100f, NEAR);
        assertThat(hidden.width()).isZero();

        row.justify(Align.END);
        step(1);
        assertThat(second.x() + second.width()).isCloseTo(1280f, NEAR);
        first.shrinkCenter();
        row.align(Align.FILL);
        step(1);
        assertThat(first.y()).isCloseTo((100f - first.minHeight()) / 2f, NEAR);
    }

    @Test
    void gridsStacksCentresMarginsPanelsAndAspect() {
        startUi();
        Node<?> cell = spacer().expand(0f).size(40f, 20f);
        Node<?> wide = spacer().expand(0f).size(80f, 30f);
        Node<?> grow = spacer().expandX(1f).minSize(10f, 10f);
        Grid grid = grid(2, cell, wide, grow).hGap(4f).vGap(6f);
        assertThat(grid.columns()).isEqualTo(2);
        Label corner = label("corner").anchor(Anchor.BOTTOM_RIGHT).offset(8f, 8f);
        Label wideBar = label("bar").anchor(Anchor.TOP_WIDE).offset(10f, 0f);
        Node<?> inner = spacer().size(100f, 50f);
        Node<?> boxed = spacer().size(30f, 30f);
        Node<?> ratio = spacer();
        Stack root = stack(
                grid.anchor(Anchor.TOP_LEFT),
                corner,
                wideBar,
                center(inner),
                margin(Insets.all(20f), panel(boxed)).anchor(Anchor.LEFT),
                aspect(2f, ratio).anchor(Anchor.RIGHT).size(200f, 300f));
        open(root);
        assertThat(grid.width()).isCloseTo(40f + 4f + 80f, NEAR);
        assertThat(wide.x()).isCloseTo(44f, NEAR);
        assertThat(grow.y()).isCloseTo(30f + 6f, NEAR);
        assertThat(grow.width()).isEqualTo(40f);
        assertThat(corner.x() + corner.width()).isCloseTo(1280f - 8f, NEAR);
        assertThat(corner.y() + corner.height()).isCloseTo(720f - 8f, NEAR);
        assertThat(wideBar.x()).isEqualTo(10f);
        assertThat(wideBar.width()).isCloseTo(1260f, NEAR);
        assertThat(inner.x()).isCloseTo(590f, NEAR);
        assertThat(inner.y()).isCloseTo(335f, NEAR);
        assertThat(boxed.x()).isGreaterThan(20f);
        assertThat(boxed.width()).isEqualTo(30f);
        assertThat(ratio.width()).isCloseTo(200f, NEAR);
        assertThat(ratio.height()).isCloseTo(100f, NEAR);
        assertThat(root.find("missing")).isNull();
        assertThatThrownBy(() -> grid(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> aspect(0f, spacer())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Anchor(1f, 0f, 0f, 0f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> root.add(root)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void wrappingTextAndFlowsReflowToTheirWidth() {
        startUi();
        Label text = label("one two three four five six seven eight nine ten eleven twelve")
                .wrap(true);
        Flow flow = flow();
        for (int i = 0; i < 12; i++) {
            flow.add(spacer().expand(0f).size(60f, 20f));
        }
        flow.gap(5f);
        Column column = column(text, flow).width(200f).anchor(Anchor.TOP_LEFT);
        open(stack(column));
        assertThat(text.width()).isEqualTo(200f);
        assertThat(text.height()).isGreaterThan(text.style(s -> {}).minHeight() / 2f);
        assertThat(flow.height()).isCloseTo(4 * 20f + 3 * 5f, NEAR);
        column.width(400f);
        step(2);
        assertThat(flow.height()).isCloseTo(2 * 20f + 5f, NEAR);
        Label clipped = label("a very long line that will be cut")
                .maxLines(1)
                .wrap(true)
                .width(60f);
        column.add(clipped);
        step(2);
        assertThat(clipped.text().plain()).startsWith("a very");
    }

    @Test
    void scrollAndSplitContainers() {
        startUi();
        Column tall = column();
        for (int i = 0; i < 40; i++) {
            tall.add(button("Row " + i).id("row" + i));
        }
        Scroll scroll = scroll(tall);
        Node<?> left = spacer();
        Node<?> right = spacer();
        Split split = split(left, right).ratio(0.25f);
        open(column(scroll.height(200f), split.height(100f)).gap(0f));
        assertThat(scroll.canScroll(false)).isTrue();
        assertThat(scroll.canScroll(true)).isFalse();
        assertThat(scroll.maxScrollY()).isGreaterThan(200f);
        Node<?> last = tall.find("row39");
        scroll.scrollTo(last);
        step(1);
        assertThat(last.y() + last.height()).isCloseTo(200f, NEAR);
        assertThat(scroll.scrollY()).isEqualTo(scroll.maxScrollY());
        scroll.scrollTo(0f, 0f);
        assertThat(scroll.scrollBy(0f, 30f)).isTrue();
        scroll.horizontal(Scroll.ScrollPolicy.ALWAYS).vertical(Scroll.ScrollPolicy.NEVER);
        step(1);
        assertThat(scroll.maxScrollY()).isZero();
        assertThat(left.width()).isCloseTo((1280f - 6f) * 0.25f, NEAR);
        assertThat(split.ratio()).isEqualTo(0.25f);
        split.vertical().ratio(2f);
        step(1);
        assertThat(split.ratio()).isEqualTo(1f);
        assertThat(right.height()).isZero();
        assertThat(new Spacer().minWidth()).isZero();
    }
}
