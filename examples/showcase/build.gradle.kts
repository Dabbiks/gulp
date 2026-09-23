plugins {
    id("gulp.java-conventions")
    application
}

description = "Showcase: every framework feature on its own screen."

dependencies {
    // Game code sees only the API; the backend is chosen at runtime.
    implementation(project(":gulp-api"))
    runtimeOnly(project(":gulp-backend-desktop"))
    annotationProcessor(project(":gulp-processor"))
}

application {
    mainClass = "dev.gulp.examples.showcase.ShowcaseGame"
}

// ./gradlew :examples:showcase:run -Pgulp.exitAfterFrames=120 closes the window by itself (used by CI).
val exitAfterFrames = providers.gradleProperty("gulp.exitAfterFrames")
tasks.named<JavaExec>("run") {
    // Output goes through a pipe to Gradle, which reads UTF-8; without this, Windows would use its code page.
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    exitAfterFrames.orNull?.let { systemProperty("gulp.desktop.exitAfterFrames", it) }
}
