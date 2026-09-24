plugins {
    id("gulp.java-conventions")
    id("dev.gulp.game")
}

description = "Platformer example: two LDtk levels with parallax, collected coins and tile collisions."

dependencies {
    implementation(project(":gulp-api"))
    annotationProcessor(project(":gulp-processor"))
    desktopRuntime(project(":gulp-backend-desktop"))
    webRuntime(project(":gulp-backend-web"))
    gulpTools(project(":gulp-tools"))
}

gulp {
    mainClass = "dev.gulp.examples.platformer.PlatformerGame"
    title = "Gulp Platformer"
}
