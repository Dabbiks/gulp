package dev.gulp.api.graphics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ColorTest {

    @Test
    void rgbCreatesOpaqueColorFromHex() {
        Color color = Color.rgb(0xff8000);

        assertThat(color.r()).isEqualTo(1f);
        assertThat(color.g()).isEqualTo(128 / 255f);
        assertThat(color.b()).isZero();
        assertThat(color.a()).isEqualTo(1f);
    }

    @Test
    void rgbIgnoresHighBits() {
        assertThat(Color.rgb(0xAB000000)).isEqualTo(Color.BLACK);
    }

    @Test
    void rgbaRoundTripsThroughPackedValue() {
        int packed = 0x1d2b5380;

        assertThat(Color.rgba(packed).toRgba8888()).isEqualTo(packed);
    }

    @Test
    void withAlphaKeepsRgb() {
        Color faded = Color.WHITE.withAlpha(0.5f);

        assertThat(faded).isEqualTo(new Color(1f, 1f, 1f, 0.5f));
    }

    @Test
    void constantsHaveExpectedValues() {
        assertThat(Color.BLACK.toRgba8888()).isEqualTo(0x000000ff);
        assertThat(Color.WHITE.toRgba8888()).isEqualTo(0xffffffff);
        assertThat(Color.CLEAR.toRgba8888()).isZero();
    }

    @Test
    void rejectsComponentsOutsideUnitRange() {
        assertThatThrownBy(() -> new Color(1.5f, 0f, 0f, 1f))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("r");
        assertThatThrownBy(() -> new Color(0f, -0.1f, 0f, 1f)).hasMessageContaining("g");
        assertThatThrownBy(() -> new Color(0f, 0f, Float.NaN, 1f)).hasMessageContaining("b");
        assertThatThrownBy(() -> new Color(0f, 0f, 0f, 2f)).hasMessageContaining("a");
    }
}
