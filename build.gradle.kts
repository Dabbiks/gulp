// Shared configuration lives in the convention plugins in build-logic/.
// Each module applies one of: gulp.java-conventions, gulp.api-conventions, gulp.coverage-conventions.

plugins {
    // Loaded once here so every project shares one classloader for the build-logic plugins.
    id("dev.gulp.game") apply false
}

allprojects {
    group = "dev.gulp"
    version = "0.1.0-SNAPSHOT"
}

// `./gradlew check` also checks the Gradle plugin, which is an included build.
tasks.register("check") {
    group = "verification"
    description = "Checks the Gradle plugin build; the modules' own check tasks run alongside."
    dependsOn(gradle.includedBuild("gulp-gradle-plugin").task(":check"))
}

tasks.register("spotlessApply") {
    group = "formatting"
    description = "Formats the Gradle plugin build; the modules' own spotlessApply tasks run alongside."
    dependsOn(gradle.includedBuild("gulp-gradle-plugin").task(":spotlessApply"))
}
