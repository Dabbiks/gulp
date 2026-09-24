package dev.gulp.api.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.asset.AssetType;
import dev.gulp.api.audio.Audio;
import dev.gulp.api.audio.PauseMode;
import dev.gulp.api.audio.Sound;
import dev.gulp.api.registry.Key;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InputApiTest {

    @Test
    void keysHaveNamesAndCachedInstances() {
        assertThat(Keys.of(44)).isSameAs(Keys.SPACE);
        assertThat(Keys.of(104).displayName()).isEqualTo("F13");
        assertThat(Keys.of(200)).isSameAs(Keys.of(200));
        assertThat(Keys.of(200).displayName()).isEqualTo("Key 200");
        assertThat(Keys.of(200).name()).isEqualTo("#200");
        assertThat(Keys.of(999).code()).isEqualTo(999);
        assertThat(Keys.byName("LEFT_SHIFT")).isSameAs(Keys.LEFT_SHIFT);
        assertThat(Keys.byName("#200")).isSameAs(Keys.of(200));
        assertThat(Keys.byName("#x")).isNull();
        assertThat(Keys.byName("nope")).isNull();
        assertThat(Keys.nameOf(0)).isNull();
        assertThat(Keys.NUM_1.displayName()).isEqualTo("1");
        assertThat(Keys.UNKNOWN.device()).isEqualTo(InputDevice.KEYBOARD);
        assertThat(Keys.W.glyph(ControllerFamily.XBOX))
                .isEqualTo(new BindingGlyph(ControllerFamily.KEYBOARD_MOUSE, "W", "input/keyboard/w"));
    }

    @Test
    void bindingIdsRoundTrip() {
        List<Binding> all = new ArrayList<>(List.of(Keys.SPACE, Keys.RIGHT_ALT, Keys.of(200)));
        all.addAll(List.of(MouseButton.values()));
        all.addAll(List.of(GamepadButton.values()));
        for (GamepadAxis axis : GamepadAxis.values()) {
            all.add(axis.positive());
            all.add(axis.negative());
        }
        for (Binding binding : all) {
            assertThat(Binding.parse(binding.id())).isEqualTo(binding);
            assertThat(binding.displayName()).isNotBlank();
            for (ControllerFamily family : ControllerFamily.values()) {
                BindingGlyph glyph = binding.glyph(family);
                assertThat(glyph.label()).isNotBlank();
                assertThat(glyph.icon()).startsWith("input/");
            }
        }
        for (String bad : List.of("space", "key:nothing", "mouse:x", "pad:x", "axis:left_x", "axis:left_x*", "joy:1")) {
            assertThatThrownBy(() -> Binding.parse(bad)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(MouseButton.ofIndex(2)).isEqualTo(MouseButton.MIDDLE);
        assertThat(MouseButton.ofIndex(7)).isNull();
        assertThat(MouseButton.BACK.device()).isEqualTo(InputDevice.MOUSE);
        assertThat(GamepadButton.LEFT_BUMPER.displayName()).isEqualTo("Left bumper");
        assertThat(GamepadButton.SOUTH.glyph(ControllerFamily.NINTENDO).label()).isEqualTo("B");
        assertThat(GamepadButton.SOUTH.glyph(ControllerFamily.KEYBOARD_MOUSE).family())
                .isEqualTo(ControllerFamily.GENERIC);
        assertThat(GamepadAxis.LEFT_TRIGGER
                        .positive()
                        .glyph(ControllerFamily.PLAYSTATION)
                        .label())
                .isEqualTo("L2");
        assertThat(GamepadAxis.RIGHT_Y.negative().glyph(ControllerFamily.XBOX).icon())
                .isEqualTo("input/xbox/right_stick_up");
        assertThat(GamepadAxis.LEFT_X.isTrigger()).isFalse();
        assertThat(GamepadAxis.RIGHT_TRIGGER.index()).isEqualTo(5);
        assertThat(GamepadAxis.LEFT_Y.positive().device()).isEqualTo(InputDevice.GAMEPAD);
    }

    @Test
    void controllerFamiliesComeFromNames() {
        assertThat(ControllerFamily.fromName("Xbox 360 Controller")).isEqualTo(ControllerFamily.XBOX);
        assertThat(ControllerFamily.fromName("054c-0ce6-DualSense")).isEqualTo(ControllerFamily.PLAYSTATION);
        assertThat(ControllerFamily.fromName("Nintendo Switch Pro Controller")).isEqualTo(ControllerFamily.NINTENDO);
        assertThat(ControllerFamily.fromName("8BitDo SN30")).isEqualTo(ControllerFamily.GENERIC);
    }

    @Test
    void actionsAndSetsValidate() {
        InputAction jump = InputAction.builder(Key.of("game", "jump"))
                .bind(Keys.SPACE, GamepadButton.SOUTH)
                .set(ActionSet.of("platforming"))
                .deadZone(0.3f)
                .build();
        assertThat(jump.defaultBindings()).containsExactly(Keys.SPACE, GamepadButton.SOUTH);
        assertThat(jump.set().name()).isEqualTo("platforming");
        assertThat(jump.deadZone()).isEqualTo(0.3f);
        assertThat(jump).isEqualTo(InputAction.builder(Key.of("game", "jump")).build());
        assertThat(jump.hashCode()).isEqualTo(Key.of("game", "jump").hashCode());
        assertThat(jump.toString()).contains("game:jump");
        assertThat(jump).isNotEqualTo("jump");
        assertThatThrownBy(() -> InputAction.builder(Key.of("game", "x")).deadZone(1f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ActionSet.of("Bad Set")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ActionSet.of("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eventsCarryTheirData() {
        KeyPressEvent press = new KeyPressEvent(Keys.A, 30, KeyEvent.SHIFT | KeyEvent.SUPER);
        assertThat(press.isShiftDown()).isTrue();
        assertThat(press.isSuperDown()).isTrue();
        assertThat(press.isAltDown()).isFalse();
        assertThat(press.isConsumedByUi()).isFalse();
        press.consumeByUi();
        assertThat(press.isConsumedByUi()).isTrue();
        MouseButtonReleaseEvent release = new MouseButtonReleaseEvent(MouseButton.RIGHT, 1, 2, 4);
        assertThat(release.button()).isEqualTo(MouseButton.RIGHT);
        assertThat(release.x() + release.y()).isEqualTo(3f);
        assertThat(release.modifiers()).isEqualTo(4);
        assertThat(new CharTypedEvent(0x1F600).text()).hasSize(2);
        assertThat(new MouseMoveEvent(1, 2, 3, 4).deltaY()).isEqualTo(4f);
        assertThat(new MouseMoveEvent(1, 2, 3, 4).y()).isEqualTo(2f);
        assertThat(new MouseScrollEvent(1, 2).deltaX()).isEqualTo(1f);
        assertThat(new TapEvent(1, 2).y()).isEqualTo(2f);
        assertThat(new DoubleTapEvent(1, 2).x()).isEqualTo(1f);
        assertThat(new LongPressEvent(1, 2).y()).isEqualTo(2f);
        PanEvent pan = new PanEvent(1, 2, 3, 4);
        assertThat(pan.x() + pan.y() + pan.deltaX() + pan.deltaY()).isEqualTo(10f);
        PinchEvent pinch = new PinchEvent(1, 2, 1.5f);
        assertThat(pinch.centerX() + pinch.centerY() + pinch.scale()).isEqualTo(4.5f);
        SwipeEvent swipe = new SwipeEvent(1, 0, 900);
        assertThat(swipe.directionX() + swipe.directionY() + swipe.speed()).isEqualTo(901f);
    }

    @Test
    void soundsValidateTheirRanges() {
        Sound sound = Sound.builder(Key.of("game", "coin"))
                .file(AssetKey.audio("game:sounds/coin"))
                .volume(0.8f)
                .pitch(1.2f)
                .bus(Audio.UI)
                .build();
        assertThat(sound.minVolume()).isEqualTo(0.8f);
        assertThat(sound.maxPitch()).isEqualTo(1.2f);
        assertThat(sound.pauseMode()).isEqualTo(PauseMode.ALWAYS);
        assertThat(sound.maxInstances()).isZero();
        assertThat(sound.minInterval()).isEqualTo(0.03f);
        assertThat(sound.priority()).isZero();
        assertThat(sound.minDistance()).isEqualTo(2f);
        assertThat(sound.maxDistance()).isEqualTo(25f);
        assertThat(sound.files()).hasSize(1);
        assertThat(sound)
                .isEqualTo(Sound.builder(Key.of("game", "coin"))
                        .file(AssetKey.audio("game:sounds/other"))
                        .build());
        assertThat(sound.hashCode()).isEqualTo(Key.of("game", "coin").hashCode());
        assertThat(sound.toString()).contains("game:coin");
        assertThat(sound).isNotEqualTo("coin");
        Sound.Builder builder = Sound.builder(Key.of("game", "x"));
        assertThatThrownBy(() -> builder.volumeRange(1f, 0.5f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.pitchRange(0f, 1f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.maxInstances(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.minInterval(-1f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.distance(5f, 5f)).isInstanceOf(IllegalArgumentException.class);
        assertThat(AssetKey.music("game:music/theme").type()).isSameAs(AssetType.MUSIC);
        assertThat(AssetType.AUDIO.extensions()).containsExactly("ogg", "wav");
    }
}
