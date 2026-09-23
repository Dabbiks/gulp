plugins {
    id("gulp.java-conventions")
}

description = "Web backend on TeaVM (Wasm GC + JS fallback): WebGL2, WebAudio, DOM, IndexedDB. Implemented in stage 3."

dependencies {
    api(project(":gulp-core"))
}
