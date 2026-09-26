plugins {
    id("gulp.java-conventions")
    id("dev.gulp.game")
}

description = "Benchmark scenes of section 20.5 (sprites, entities, particles, UI, map) with thresholds checked in CI."

dependencies {
    implementation(project(":gulp-api"))
    annotationProcessor(project(":gulp-processor"))
    desktopRuntime(project(":gulp-backend-desktop"))
    webRuntime(project(":gulp-backend-web"))
    gulpTools(project(":gulp-tools"))
    testImplementation(project(":gulp-backend-headless"))
    testAnnotationProcessor(project(":gulp-processor"))
}

gulp {
    mainClass = "dev.gulp.examples.bench.BenchGame"
    title = "Gulp Bench"
}

// Timings depend on the machine, so `check` leaves the benchmarks out; `bench` runs them (CI does, see ci.yml).
tasks.named<Test>("test") {
    useJUnitPlatform { excludeTags("bench") }
}

val bench = tasks.register<Test>("bench") {
    group = "verification"
    description = "Runs the benchmark scenes headless against the budgets of section 20.5."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("bench") }
    // -Pgulp.bench.slack=2 relaxes every budget, for slow shared CI machines.
    systemProperty("gulp.bench.slack", providers.gradleProperty("gulp.bench.slack").getOrElse("1"))
    outputs.upToDateWhen { false }
    testLogging { showStandardStreams = true }
}

// -Pgulp.bench runs every scene in a window once, logs BENCH lines and closes.
tasks.named<JavaExec>("runDesktop") {
    if (providers.gradleProperty("gulp.bench").isPresent) {
        systemProperty("gulp.bench.auto", "true")
    }
}
