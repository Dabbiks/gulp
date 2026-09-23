plugins {
    id("gulp.java-conventions")
}

description = "Web backend on TeaVM (Wasm GC + JS fallback): WebGL2, WebAudio, DOM, IndexedDB."

dependencies {
    api(project(":gulp-core"))
    compileOnly(libs.teavm.jso)
    compileOnly(libs.teavm.jso.apis)
}

// TeaVM's JSBody refers to JetBrains' @Language, which is not on the classpath.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-classfile")
}
