package dev.gulp.core;

import static dev.gulp.api.ui.Ui.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.gulp.api.render.StretchMode;
import dev.gulp.api.ui.Dropdown;
import dev.gulp.api.ui.Node;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pointer input on a scaled display: window points are mapped to the logical game area before hit testing. */
class UiDropdownMouseTest extends UiFixture {

    @Test
    void choosingAnOptionWithTheMouseOnAScaledDisplay() {
        startUi(s -> s.windowSize(800, 600).baseResolution(1024, 768).stretchMode(StretchMode.CANVAS), b -> {});
        Dropdown<String> dropdown = dropdown(List.of("Dark", "Light", "Pixel"), s -> s);
        open(column(dropdown));
        float s = 800f / 1024f;
        click((dropdown.x() + 5) * s, (dropdown.y() + 5) * s);
        Node<?> first = ui.focused();
        assertThat(first).isNotSameAs(dropdown);
        Node<?> pixel = first.parent().children().get(2);
        move((pixel.x() + 10) * s, (pixel.y() + 10) * s);
        click((pixel.x() + 10) * s, (pixel.y() + 10) * s);
        assertThat(dropdown.value()).isEqualTo("Pixel");
        assertThat(ui.focused()).isSameAs(dropdown);
    }

    @Test
    void aPressAndReleaseInOneFrameStillClicks() {
        startUi();
        dev.gulp.api.ui.Tabs tabs = tabs(tab("One", label("1")), tab("Two", label("2")));
        open(column(tabs));
        Node<?> two = tabs.children().get(0).children().get(1);
        float x = two.x() + 5f;
        float y = two.y() + 5f;
        backend().input().inject(l -> l.mouseMoved(x, y, 0f, 0f));
        backend().input().inject(l -> l.mouseButton(0, true, 0));
        backend().input().inject(l -> l.mouseButton(0, false, 0));
        step(2);
        assertThat(tabs.selected()).isEqualTo(1);
    }
}
