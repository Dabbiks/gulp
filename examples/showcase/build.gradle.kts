plugins {
    id("gulp.java-conventions")
    application
}

description = "Showcase: every framework feature on its own screen."

dependencies {
    // Game code sees only the API; the backend is chosen at runtime.
    implementation(project(":gulp-api"))
    runtimeOnly(project(":gulp-backend-desktop"))
}

application {
    mainClass = "dev.gulp.examples.showcase.ShowcaseGame"
}

// ./gradlew :examples:showcase:run -Pgulp.exitAfterFrames=120 closes the window by itself (used by CI).
val exitAfterFrames = providers.gradleProperty("gulp.exitAfterFrames")
tasks.named<JavaExec>("run") {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    exitAfterFrames.orNull?.let { systemProperty("gulp.desktop.exitAfterFrames", it) }
}
