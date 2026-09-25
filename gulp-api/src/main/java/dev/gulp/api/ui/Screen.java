package dev.gulp.api.ui;

import dev.gulp.api.Gulp;
import dev.gulp.api.Logger;
import dev.gulp.api.Owner;
import dev.gulp.api.data.Config;
import dev.gulp.api.spi.UiAccess;
import org.jspecify.annotations.Nullable;

/**
 * A full-screen UI state such as a menu, a pause screen or a dialog. {@code ui().open(screen)} replaces the stack of
 * screens, {@code push} puts one on top and {@code pop} goes back. The node tree comes from {@link #build()} each time
 * the screen opens, laid out over the whole UI area. A screen is an {@link Owner}: listeners, tasks and tweens it
 * registers end when it closes.
 *
 * <p>Settings, usually made in the constructor: {@link #pausesGame} (default off), {@link #blocksGameplayInput}
 * (default on; the {@code gameplay} action set is disabled while the screen is open and clicks do not reach the world),
 * {@link #dimBackground} (default off) and the {@link #enter} and {@link #exit} transitions. Cancel ({@code
 * ui_cancel}) calls {@link #onBack()}, which pops the screen.
 *
 * <pre>{@code
 * public final class PauseScreen extends Screen {
 *     public PauseScreen() {
 *         pausesGame(true).dimBackground(true);
 *     }
 *
 *     @Override
 *     protected Node<?> build() {
 *         return center(column(
 *                         label(tr("pause.title")).variant("title"),
 *                         button(tr("pause.resume")).onClick(ui()::pop),
 *                         button(tr("pause.settings")).onClick(() -> ui().push(new SettingsScreen())),
 *                         button(tr("pause.quit")).variant("danger").onClick(() -> ui().open(new MainMenu())))
 *                 .gap(12).width(260));
 *     }
 * }
 * }</pre>
 */
public abstract class Screen implements Owner {

    private boolean pausesGame;
    private boolean blocksGameplayInput = true;
    private boolean dimBackground;
    private ScreenTransition enter = ScreenTransition.fade(0.15f);
    private ScreenTransition exit = ScreenTransition.fade(0.12f);
    private boolean open;
    private @Nullable Node<?> root;

    /** Creates a screen. */
    protected Screen() {}

    /**
     * Builds the node tree; called every time the screen opens.
     *
     * @return the root, laid out over the whole UI area (or by its anchor)
     */
    protected abstract Node<?> build();

    /** Called after the screen opened and its tree was built. */
    protected void onOpen() {}

    /** Called when the screen closes, before its tree is removed. */
    protected void onClose() {}

    /** Called on cancel ({@code ui_cancel}: Escape, the east button); the default closes this screen. */
    protected void onBack() {
        close();
    }

    /**
     * Returns the node focused when the screen opens.
     *
     * @return the node, or {@code null} for the first focusable one
     */
    protected @Nullable Node<?> defaultFocus() {
        return null;
    }

    /**
     * Closes this screen, wherever it is on the stack.
     */
    public final void close() {
        if (open && UiAccess.hasBackend()) {
            Gulp.engine().ui().close(this);
        }
    }

    /**
     * Returns whether the screen is on the stack.
     *
     * @return {@code true} while open
     */
    public final boolean isOpen() {
        return open;
    }

    /**
     * Returns the node tree built at opening.
     *
     * @return the root, or {@code null} while closed
     */
    public final @Nullable Node<?> root() {
        return root;
    }

    /**
     * Pauses the game while this screen is open.
     *
     * @param value whether to pause
     * @return this screen
     */
    public final Screen pausesGame(boolean value) {
        this.pausesGame = value;
        return this;
    }

    /**
     * Returns whether the game pauses while this screen is open.
     *
     * @return {@code false} by default
     */
    public final boolean pausesGame() {
        return pausesGame;
    }

    /**
     * Disables the {@code gameplay} action set and keeps clicks from the world while this screen is open.
     *
     * @param value whether to block
     * @return this screen
     */
    public final Screen blocksGameplayInput(boolean value) {
        this.blocksGameplayInput = value;
        return this;
    }

    /**
     * Returns whether gameplay input is blocked.
     *
     * @return {@code true} by default
     */
    public final boolean blocksGameplayInput() {
        return blocksGameplayInput;
    }

    /**
     * Darkens everything below this screen (the theme's {@code screen} background).
     *
     * @param value whether to dim
     * @return this screen
     */
    public final Screen dimBackground(boolean value) {
        this.dimBackground = value;
        return this;
    }

    /**
     * Returns whether the background is dimmed.
     *
     * @return {@code false} by default
     */
    public final boolean dimBackground() {
        return dimBackground;
    }

    /**
     * Sets how the screen appears.
     *
     * @param transition the transition; a short fade by default
     * @return this screen
     */
    public final Screen enter(ScreenTransition transition) {
        this.enter = transition;
        return this;
    }

    /**
     * Returns how the screen appears.
     *
     * @return the transition
     */
    public final ScreenTransition enter() {
        return enter;
    }

    /**
     * Sets how the screen disappears.
     *
     * @param transition the transition; a short fade by default
     * @return this screen
     */
    public final Screen exit(ScreenTransition transition) {
        this.exit = transition;
        return this;
    }

    /**
     * Returns how the screen disappears.
     *
     * @return the transition
     */
    public final ScreenTransition exit() {
        return exit;
    }

    // ------------------------------------------------------------------ Owner

    @Override
    public String id() {
        return "screen:" + getClass().getName();
    }

    @Override
    public final boolean isEnabled() {
        return open;
    }

    @Override
    public Logger logger() {
        return Gulp.engine().game().logger();
    }

    @Override
    public Config config() {
        return Gulp.engine().game().config();
    }

    /** Engine access to the protected lifecycle. */
    private static final class ScreenHooks implements UiAccess.ScreenHooks {
        @Override
        public Node<?> open(Screen screen) {
            screen.open = true;
            Node<?> built = screen.build();
            screen.root = built;
            return built;
        }

        @Override
        public void opened(Screen screen) {
            screen.onOpen();
        }

        @Override
        public void close(Screen screen) {
            try {
                screen.onClose();
            } finally {
                screen.open = false;
                screen.root = null;
            }
        }

        @Override
        public void back(Screen screen) {
            screen.onBack();
        }

        @Override
        public @Nullable Node<?> defaultFocus(Screen screen) {
            return screen.defaultFocus();
        }
    }

    /**
     * Installs the engine hooks of screens. Called by {@link UiAccess}; an explicit call, not a static initializer,
     * because TeaVM initializes classes lazily.
     */
    public static void loadHooks() {
        UiAccess.installScreens(new ScreenHooks());
    }
}
