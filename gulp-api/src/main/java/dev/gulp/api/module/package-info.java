/**
 * Modules: separate parts of a game with their own lifecycle and dependencies.
 *
 * <pre>{@code
 * @ModuleInfo(id = "hud")
 * public final class HudModule extends GameModule {
 *     @Override public void onEnable() { every(20, this::refresh); }
 * }
 * }</pre>
 */
@NullMarked
package dev.gulp.api.module;

import org.jspecify.annotations.NullMarked;
