/**
 * Navigation: the tile-based {@link dev.gulp.api.nav.NavGrid} with A*, Jump Point Search and flow fields, A* on any
 * {@link dev.gulp.api.nav.Graph}, paths, and the {@link dev.gulp.api.nav.NavAgent} and {@link
 * dev.gulp.api.nav.PathFollower} components.
 *
 * <pre>{@code
 * zombie.get(NavAgent.class).follow(player);
 * lift.add(new PathFollower(Path.of(bottom, top)).pingPong(true));
 * }</pre>
 */
@NullMarked
package dev.gulp.api.nav;

import org.jspecify.annotations.NullMarked;
