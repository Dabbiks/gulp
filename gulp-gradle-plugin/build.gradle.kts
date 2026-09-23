plugins {
    id("gulp.java-conventions")
    `java-gradle-plugin`
}

description = "Gradle plugin for game projects (id dev.gulp.game)."

dependencies {
    implementation(project(":gulp-tools"))
    compileOnly(libs.jspecify)
}

gradlePlugin {
    plugins {
        create("gulpGame") {
            id = "dev.gulp.game"
            implementationClass = "dev.gulp.gradle.GulpGamePlugin"
            displayName = "Gulp game"
            description = "Builds, runs and packages Gulp games for desktop and web."
        }
    }
}
