/**
 * Platform SPI: the interfaces every backend (desktop, web, headless) implements in full. Only gulp-core and backends
 * see this package; {@code gulp-core} never knows which platform it runs on.
 *
 * <p>Rules shared by all interfaces here:
 *
 * <ul>
 *   <li>Every method is called on the main game thread unless its Javadoc says otherwise.
 *   <li>Asynchronous operations report through {@link dev.gulp.platform.PlatformCallback}; the backend invokes the
 *       callback on the main thread, between frames. {@code gulp-core} wraps these callbacks in its own
 *       {@code Promise}.
 *   <li>Buffers passed to or returned from the platform are direct {@link java.nio.ByteBuffer}s in native byte order.
 * </ul>
 *
 * <pre>{@code
 * PlatformBackend backend = ...;
 * backend.loop().run(new FrameHandler() {
 *     public boolean frame(long nanoTime) {
 *         backend.gl().clear(Gl.COLOR_BUFFER_BIT);
 *         return !backend.window().shouldClose();
 *     }
 *     public void exit() {}
 * });
 * }</pre>
 */
@NullMarked
package dev.gulp.platform;

import org.jspecify.annotations.NullMarked;
