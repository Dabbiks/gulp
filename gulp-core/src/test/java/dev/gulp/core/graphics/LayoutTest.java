package dev.gulp.core.graphics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.render.AspectMode;
import dev.gulp.api.render.StretchMode;
import org.junit.jupiter.api.Test;

class LayoutTest {

    @Test
    void disabledStretchUsesWindowPoints() {
        DisplayLayout layout =
                DisplayLayout.compute(1600, 1200, 2f, 320, 180, StretchMode.DISABLED, AspectMode.KEEP, false);
        assertThat(layout).isEqualTo(new DisplayLayout(0, 0, 1600, 1200, 800, 600));
    }

    @Test
    void keepAddsBarsAndIntegerScalingRoundsDown() {
        DisplayLayout keep = DisplayLayout.compute(1000, 600, 1f, 320, 180, StretchMode.CANVAS, AspectMode.KEEP, false);
        assertThat(keep.viewportHeight()).isEqualTo(563);
        assertThat(keep.viewportWidth()).isEqualTo(1000);
        assertThat(keep.viewportY()).isEqualTo(18);
        assertThat(keep.logicalWidth()).isEqualTo(320);

        DisplayLayout integer =
                DisplayLayout.compute(1000, 600, 1f, 320, 180, StretchMode.VIEWPORT, AspectMode.KEEP, true);
        assertThat(integer).isEqualTo(new DisplayLayout(20, 30, 960, 540, 320, 180));
        assertThat(integer.scaleX()).isEqualTo(3f);
    }

    @Test
    void otherAspectModes() {
        assertThat(DisplayLayout.compute(1000, 600, 1f, 320, 180, StretchMode.CANVAS, AspectMode.IGNORE, false))
                .isEqualTo(new DisplayLayout(0, 0, 1000, 600, 320, 180));
        DisplayLayout expand =
                DisplayLayout.compute(1280, 1080, 1f, 320, 180, StretchMode.CANVAS, AspectMode.EXPAND, false);
        assertThat(expand.logicalWidth()).isEqualTo(320f);
        assertThat(expand.logicalHeight()).isEqualTo(270f);
        DisplayLayout keepWidth =
                DisplayLayout.compute(640, 720, 1f, 320, 180, StretchMode.CANVAS, AspectMode.KEEP_WIDTH, false);
        assertThat(keepWidth.logicalWidth()).isEqualTo(320f);
        assertThat(keepWidth.logicalHeight()).isEqualTo(360f);
        DisplayLayout keepHeight =
                DisplayLayout.compute(1280, 360, 1f, 320, 180, StretchMode.CANVAS, AspectMode.KEEP_HEIGHT, false);
        assertThat(keepHeight.logicalWidth()).isEqualTo(640f);
        assertThat(keepHeight.logicalHeight()).isEqualTo(180f);
    }

    @Test
    void cameraMapsWorldToScreen() {
        CameraImpl camera = new CameraImpl(16);
        camera.resize(320, 180);
        assertThat(camera.worldToScreen(Vec2.ZERO)).isEqualTo(new Vec2(160, 90));
        camera.setPosition(new Vec2(10, 5));
        camera.setZoom(2f);
        Vec2 screen = camera.worldToScreen(new Vec2(11, 5));
        assertThat(screen.x()).isCloseTo(192f, within(1e-3f));
        Vec2 back = camera.screenToWorld(screen);
        assertThat(back.x()).isCloseTo(11f, within(1e-4f));
        assertThat(back.y()).isCloseTo(5f, within(1e-4f));
        Rect bounds = camera.bounds();
        assertThat(bounds.width()).isCloseTo(10f, within(1e-4f));
        assertThat(bounds.height()).isCloseTo(5.625f, within(1e-4f));

        camera.setRotation(90f);
        assertThat(camera.bounds().width()).isCloseTo(5.625f, within(1e-3f));

        // The clip-space projection agrees with the screen transform.
        var clip = camera.projection(new dev.gulp.api.math.Affine2(), 1f, 1f);
        var toScreen = camera.worldToScreen(new dev.gulp.api.math.Affine2());
        float wx = 12f;
        float wy = 7f;
        float sx = toScreen.transformX(wx, wy);
        float sy = toScreen.transformY(wx, wy);
        assertThat(clip.transformX(wx, wy)).isCloseTo(sx / 160f - 1f, within(1e-4f));
        assertThat(clip.transformY(wx, wy)).isCloseTo(1f - sy / 90f, within(1e-4f));
    }

    @Test
    void multiplyPackedColors() {
        assertThat(DrawImpl.multiply(0x80402010, 0xffffffff)).isEqualTo(0x80402010);
        assertThat(DrawImpl.multiply(0xffffffff, 0x80808080)).isEqualTo(0x80808080);
        assertThat(DrawImpl.multiply(0xff0000ff, 0x00000000)).isZero();
    }
}
