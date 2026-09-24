package dev.gulp.examples.showcase;

import dev.gulp.api.audio.Audio;
import dev.gulp.api.audio.Bus;
import dev.gulp.api.audio.PcmSource;
import dev.gulp.api.audio.Playback;
import dev.gulp.api.audio.Sound;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.input.Binding;
import dev.gulp.api.input.Cursor;
import dev.gulp.api.input.CursorMode;
import dev.gulp.api.input.Gamepad;
import dev.gulp.api.input.GamepadAxis;
import dev.gulp.api.input.GamepadButton;
import dev.gulp.api.input.GamepadConnectEvent;
import dev.gulp.api.input.InputAction;
import dev.gulp.api.input.KeyPressEvent;
import dev.gulp.api.input.Keys;
import dev.gulp.api.input.MouseButton;
import dev.gulp.api.input.SystemCursor;
import dev.gulp.api.input.TapEvent;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.module.GameModule;
import dev.gulp.api.module.ModuleInfo;
import dev.gulp.api.registry.Registries;
import dev.gulp.api.render.Draw;
import dev.gulp.api.render.RenderLayerEvent;
import dev.gulp.api.text.TextStyle;
import java.util.ArrayList;
import java.util.List;

/**
 * Stage 5 demo: device-independent actions with keyboard, mouse, touch and gamepad; rebinding saved in preferences
 * (it survives a restart), button prompts for the controller in use, sounds with variants and limits, streamed music
 * with a loop, a procedural tone, buses with a low-pass filter, and cursors.
 *
 * <p>Keys on this screen: R rebinds jump, Backspace resets it, M music, T tone, L muffle, 1 and 2 volume, C cursor.
 */
@ModuleInfo(id = "input")
final class InputAudioDemoModule extends GameModule {

    private static final Color PANEL = Color.rgba(0x10182cdd);
    private static final Color DIM = Color.rgb(0xc2c3c7);

    private InputAction left;
    private InputAction right;
    private InputAction up;
    private InputAction down;
    private InputAction jump;
    private InputAction fire;
    private Sound jumpSound;
    private Sound coinSound;

    private float playerX = 160;
    private float playerY = 200;
    private float jumpHeight;
    private float jumpSpeed;
    private String status = "";
    private Playback tone;
    private int cursorIndex;
    private final List<String> lastEvents = new ArrayList<>();

    @Override
    public void onLoad() {
        var registries = registries();
        left = registries.register(
                Registries.INPUT_ACTION,
                InputAction.builder(key("move_left"))
                        .bind(Keys.A, Keys.LEFT, GamepadAxis.LEFT_X.negative(), GamepadButton.DPAD_LEFT)
                        .build());
        right = registries.register(
                Registries.INPUT_ACTION,
                InputAction.builder(key("move_right"))
                        .bind(Keys.D, Keys.RIGHT, GamepadAxis.LEFT_X.positive(), GamepadButton.DPAD_RIGHT)
                        .build());
        up = registries.register(
                Registries.INPUT_ACTION,
                InputAction.builder(key("move_up"))
                        .bind(Keys.W, Keys.UP, GamepadAxis.LEFT_Y.negative(), GamepadButton.DPAD_UP)
                        .build());
        down = registries.register(
                Registries.INPUT_ACTION,
                InputAction.builder(key("move_down"))
                        .bind(Keys.S, Keys.DOWN, GamepadAxis.LEFT_Y.positive(), GamepadButton.DPAD_DOWN)
                        .build());
        jump = registries.register(
                Registries.INPUT_ACTION,
                InputAction.builder(key("jump"))
                        .bind(Keys.SPACE, GamepadButton.SOUTH)
                        .build());
        fire = registries.register(
                Registries.INPUT_ACTION,
                InputAction.builder(key("fire"))
                        .bind(MouseButton.LEFT, Keys.F, GamepadAxis.RIGHT_TRIGGER.positive())
                        .build());
        jumpSound = registries.register(
                Registries.SOUND,
                Sound.builder(key("jump"))
                        .file(GameAssets.Sounds.JUMP)
                        .pitchRange(0.95f, 1.05f)
                        .build());
        coinSound = registries.register(
                Registries.SOUND,
                Sound.builder(key("coin"))
                        .file(GameAssets.Sounds.COIN_A)
                        .file(GameAssets.Sounds.COIN_B)
                        .volumeRange(0.7f, 1f)
                        .maxInstances(3)
                        .minInterval(0.06f)
                        .build());
    }

    @Override
    public void onEnable() {
        every(1, this::tick);
        on(KeyPressEvent.class, this::key);
        on(GamepadConnectEvent.class, e -> remember("Pad: " + e.gamepad().name()));
        on(TapEvent.class, e -> remember("Tap " + Math.round(e.x()) + ", " + Math.round(e.y())));
        on(RenderLayerEvent.class, e -> {
            if (e.layer().name().equals("overlay") && ShowcaseGame.screen() == 1) {
                draw(e.draw());
            }
        });
    }

    private void remember(String event) {
        lastEvents.add(0, event);
        if (lastEvents.size() > 3) {
            lastEvents.remove(3);
        }
    }

    private void tick() {
        if (ShowcaseGame.screen() != 1) {
            return;
        }
        Vec2 move = input().vector(left, right, up, down);
        playerX = Math.max(40, Math.min(360, playerX + move.x() * 4f));
        playerY = Math.max(150, Math.min(420, playerY + move.y() * 4f));
        if (input().justPressed(jump) && jumpHeight == 0f) {
            jumpSpeed = 9f;
            audio().playAt(jumpSound, (playerX - 200f) / 16f, 0f);
        }
        if (jumpSpeed != 0f || jumpHeight > 0f) {
            jumpHeight = Math.max(0f, jumpHeight + jumpSpeed);
            jumpSpeed -= 0.8f;
            if (jumpHeight == 0f) {
                jumpSpeed = 0f;
            }
        }
        if (input().justPressed(fire)) {
            audio().play(coinSound);
            for (Gamepad pad : input().gamepads()) {
                pad.rumble(0.2f, 0.6f, 0.15f);
            }
        }
    }

    private void key(KeyPressEvent event) {
        if (ShowcaseGame.screen() != 1) {
            return;
        }
        var bindings = input().bindings();
        if (event.key().equals(Keys.R) && !bindings.isCapturing()) {
            status = "Naciśnij klawisz, przycisk myszy lub pada dla skoku…";
            bindings.captureNextInput(binding -> {
                bindings.rebind(jump, 0, binding);
                List<InputAction> clashes = bindings.conflicts(jump, binding);
                status = "Skok: " + binding.displayName()
                        + (clashes.isEmpty()
                                ? ""
                                : " (koliduje z " + clashes.get(0).key().path() + ")");
            });
        } else if (event.key().equals(Keys.BACKSPACE)) {
            bindings.reset(jump);
            status = "Przywrócono domyślne przypisania skoku";
        } else if (event.key().equals(Keys.M)) {
            if (audio().music().isPlaying()) {
                audio().music().stop(1.5f);
            } else {
                audio().music().play(GameAssets.Music.LOOP, 1.5f);
            }
        } else if (event.key().equals(Keys.T)) {
            toggleTone();
        } else if (event.key().equals(Keys.L)) {
            Bus master = audio().bus(Audio.MASTER);
            master.setLowpass(master.lowpass() > 0f ? 0f : 700f);
        } else if (event.key().equals(Keys.NUM_1) || event.key().equals(Keys.NUM_2)) {
            Bus master = audio().bus(Audio.MASTER);
            master.setVolume(master.volume() + (event.key().equals(Keys.NUM_1) ? -0.1f : 0.1f));
        } else if (event.key().equals(Keys.C)) {
            cursorIndex = (cursorIndex + 1) % 4;
            input().setCursor(
                            switch (cursorIndex) {
                                case 1 -> SystemCursor.HAND;
                                case 2 -> SystemCursor.CROSSHAIR;
                                case 3 -> Cursor.custom(assets().region("showcase:sprites/coin"), 8, 8);
                                default -> SystemCursor.ARROW;
                            });
            input().setCursorMode(CursorMode.NORMAL);
        }
    }

    private void toggleTone() {
        if (tone != null && tone.isPlaying()) {
            tone.fadeOut(0.3f);
            return;
        }
        tone = audio().stream(
                        new PcmSource() {
                            private double phase;
                            private long frame;

                            @Override
                            public int channels() {
                                return 1;
                            }

                            @Override
                            public int sampleRate() {
                                return 22_050;
                            }

                            @Override
                            public int read(float[] samples, int frames) {
                                for (int i = 0; i < frames; i++) {
                                    double wobble = 1 + 0.03 * Math.sin(frame++ * 2 * Math.PI * 5 / 22_050);
                                    phase += 2 * Math.PI * 330 * wobble / 22_050;
                                    samples[i] = (float) (Math.sin(phase) * 0.15);
                                }
                                return frames;
                            }
                        },
                        Audio.SFX)
                .setVolume(0.8f);
    }

    private void draw(Draw draw) {
        float x = 24;
        float y = 60;
        float width = Math.min(display().width() - 48, 900);
        draw.color(PANEL).roundedRect(new Rect(x - 12, y - 12, width + 24, 440), 12);

        // The player: moves with the move actions, jumps with jump, flashes with fire.
        draw.color(Color.rgb(0x5f574f)).rect(x, 140, 360, 300);
        draw.color(input().pressed(fire) ? Color.rgb(0xffec27) : Color.rgb(0xff004d))
                .circle(playerX, playerY - jumpHeight, 14);
        draw.color(Color.WHITE);
        draw.text("Wejście i dźwięk", x, y, TextStyle.of(30));
        draw.text(
                "Ruch " + describe(left) + " / " + describe(right) + " / " + describe(up) + " / " + describe(down),
                x,
                y + 44,
                TextStyle.of(14).color(DIM));
        Vec2 vector = input().vector(left, right, up, down);
        draw.text(
                "wektor " + fixed(vector.x(), 2) + ", " + fixed(vector.y(), 2) + "   oś "
                        + fixed(input().axis(left, right), 2),
                x,
                y + 64,
                TextStyle.of(14).color(DIM));

        float column = x + 390;
        float line = y + 90;
        line = row(draw, column, line, "Skok: " + describe(jump) + (input().pressed(jump) ? "  ●" : ""));
        line = row(draw, column, line, "Strzał: " + describe(fire) + "  ticki: " + input().heldTicks(fire));
        line = row(draw, column, line, "Urządzenie: " + input().lastDevice() + " (" + input().controllerFamily() + ")");
        List<Gamepad> pads = input().gamepads();
        line = row(
                draw,
                column,
                line,
                pads.isEmpty() ? "Pad: brak" : "Pad: " + pads.get(0).name());
        line = row(
                draw,
                column,
                line,
                "Muzyka: " + (audio().music().isPlaying() ? "gra " : "cisza ")
                        + fixed(audio().music().position(), 1) + " s   głosy: " + audio().activeVoices() + "/"
                        + audio().maxVoices());
        Bus master = audio().bus(Audio.MASTER);
        line = row(
                draw,
                column,
                line,
                "Głośność: " + Math.round(master.volume() * 100) + "%   filtr: "
                        + (master.lowpass() > 0 ? "700 Hz" : "brak"));
        if (!audio().isUnlocked()) {
            line = row(draw, column, line, "Kliknij, aby włączyć dźwięk");
        }
        for (String event : lastEvents) {
            line = row(draw, column, line, event);
        }
        draw.color(Color.rgb(0xffec27));
        row(draw, column, line + 6, status);
        draw.color(Color.WHITE);
        draw.text(
                "R: zmień skok   Backspace: domyślne   M: muzyka   T: ton   L: filtr   1/2: głośność   C: kursor",
                x,
                y + 400,
                TextStyle.of(13).color(DIM));
    }

    /** Formats a number with a fixed number of decimals; String.format is heavy on the web. */
    private static String fixed(float value, int decimals) {
        float scale = decimals == 1 ? 10f : 100f;
        long scaled = Math.round(Math.abs(value) * scale);
        String digits = Long.toString(scaled + (long) scale).substring(1);
        return (value < 0 && scaled != 0 ? "-" : "") + (scaled / (long) scale) + "." + digits;
    }

    private static float row(Draw draw, float x, float y, String text) {
        draw.text(text, x, y, TextStyle.of(15));
        return y + 24;
    }

    private String describe(InputAction action) {
        StringBuilder text = new StringBuilder();
        for (Binding binding : input().bindings().of(action)) {
            if (binding == null) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append(", ");
            }
            text.append(binding.glyph().label());
        }
        return text.toString();
    }
}
