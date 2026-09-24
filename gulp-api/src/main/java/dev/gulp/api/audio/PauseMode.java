package dev.gulp.api.audio;

/**
 * Whether a sound pauses when the game pauses.
 *
 * <pre>{@code
 * Sound click = Sound.builder(key("click")).file(GameAssets.Sounds.CLICK).pauseMode(PauseMode.ALWAYS).build();
 * }</pre>
 */
public enum PauseMode {
    /** Pauses with the game and resumes with it. */
    GAME,
    /** Keeps playing while the game is paused (menus, UI, music). */
    ALWAYS
}
