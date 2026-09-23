plugins {
    id("gulp.java-conventions")
    id("dev.gulp.game")
}

description = "Showcase: every framework feature on its own screen."

dependencies {
    // Game code sees only the API; the backend comes from the task: runDesktop or buildWeb / runWeb.
    implementation(project(":gulp-api"))
    annotationProcessor(project(":gulp-processor"))
    desktopRuntime(project(":gulp-backend-desktop"))
    webRuntime(project(":gulp-backend-web"))
    gulpTools(project(":gulp-tools"))
}

gulp {
    mainClass = "dev.gulp.examples.showcase.ShowcaseGame"
    title = "Gulp Showcase"
    assets {
        // A crisp pixel-style font from Fira Sans (SIL OFL), generated at build time.
        bitmapFont("showcase:fonts/pixel", file("fonts-src/FiraSans-Regular.ttf"), 14, false)
    }
}

// Smoke test of the web build in real browsers (Chromium, Firefox, WebKit) through Playwright:
// ./gradlew :examples:showcase:webSmokeTest [-Pgulp.browsers=chromium]
val webSmokeTestSources = sourceSets.create("webSmokeTest")
dependencies {
    "webSmokeTestImplementation"(libs.playwright)
    "webSmokeTestImplementation"(platform(libs.junit.bom))
    "webSmokeTestImplementation"(libs.junit.jupiter)
    "webSmokeTestImplementation"(libs.assertj.core)
    "webSmokeTestRuntimeOnly"(libs.junit.platform.launcher)
}
tasks.register<Test>("webSmokeTest") {
    description = "Loads build/web in headless browsers and checks that the showcase starts without console errors."
    group = "verification"
    testClassesDirs = webSmokeTestSources.output.classesDirs
    classpath = webSmokeTestSources.runtimeClasspath
    dependsOn("buildWeb")
    useJUnitPlatform()
    systemProperty("gulp.web.dir", layout.buildDirectory.dir("web").get().asFile.path)
    systemProperty("gulp.browsers", providers.gradleProperty("gulp.browsers").getOrElse("chromium,firefox,webkit"))
    outputs.upToDateWhen { false }
}
tasks.register<JavaExec>("installBrowsers") {
    description = "Downloads the Playwright browsers (and on Linux their system libraries) for webSmokeTest."
    group = "verification"
    classpath = webSmokeTestSources.runtimeClasspath
    mainClass = "com.microsoft.playwright.CLI"
    args("install", "--with-deps", "chromium", "firefox", "webkit")
}
