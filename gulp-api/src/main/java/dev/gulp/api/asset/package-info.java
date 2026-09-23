/**
 * Assets: typed keys, asynchronous loading with reference counting, groups, custom loaders and the startup loading
 * screen.
 *
 * <pre>{@code
 * assets().load(AssetKey.texture("coins:sprites/player")).thenSync(texture -> player = texture);
 * }</pre>
 */
@NullMarked
package dev.gulp.api.asset;

import org.jspecify.annotations.NullMarked;
