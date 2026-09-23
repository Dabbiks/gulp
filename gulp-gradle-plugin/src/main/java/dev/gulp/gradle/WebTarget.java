package dev.gulp.gradle;

/**
 * Main output of a web build.
 *
 * <pre>{@code
 * gulp { web { target = WebTarget.WASM_GC } }
 * }</pre>
 */
public enum WebTarget {
    /** WebAssembly with garbage collection: smaller and faster; Chrome 119+, Firefox 120+, Safari 18.2+. */
    WASM_GC,
    /** JavaScript: runs in every browser with WebGL2. */
    JS
}
