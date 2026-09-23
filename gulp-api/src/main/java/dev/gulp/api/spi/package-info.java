/**
 * Extension points implemented by backends. Game code does not use this package.
 *
 * <p>Example registration in a backend's {@code META-INF/services/dev.gulp.api.spi.GameLauncher}:
 *
 * <pre>{@code
 * dev.gulp.backend.desktop.DesktopGameLauncher
 * }</pre>
 */
@NullMarked
package dev.gulp.api.spi;

import org.jspecify.annotations.NullMarked;
