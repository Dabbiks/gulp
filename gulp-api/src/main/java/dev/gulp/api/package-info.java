/**
 * Public Gulp API. Game code imports only packages under {@code dev.gulp.api}.
 *
 * <p>Entry point of every game:
 *
 * <pre>{@code
 * public final class MyGame extends Game {
 *     public static void main(String[] args) {
 *         Gulp.launch(new MyGame());
 *     }
 *
 *     @Override public String id() { return "mygame"; }
 *
 *     @Override public void onStart() {}
 * }
 * }</pre>
 */
@NullMarked
package dev.gulp.api;

import org.jspecify.annotations.NullMarked;
