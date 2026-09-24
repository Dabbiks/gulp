package dev.gulp.api;

/**
 * Whether something stops when the game pauses: sounds and entities.
 *
 * <pre>{@code
 * Sound click = Sound.builder(key("click")).file(GameAssets.Sounds.CLICK).pauseMode(PauseMode.ALWAYS).build();
 * pauseMenuCursor.setPauseMode(PauseMode.ALWAYS); // an entity that keeps ticking in the pause menu
 * }</pre>
 */
public enum PauseMode {
    /** Pauses with the game and resumes with it. */
    GAME,
    /** Keeps running while the game is paused (menus, UI, music). */
    ALWAYS
}
