plugins {
    id("gulp.java-conventions")
    id("dev.gulp.game")
}

description = "Topdown example: an endless island world generated from noise in chunks."

dependencies {
    implementation(project(":gulp-api"))
    annotationProcessor(project(":gulp-processor"))
    desktopRuntime(project(":gulp-backend-desktop"))
    webRuntime(project(":gulp-backend-web"))
    gulpTools(project(":gulp-tools"))
}

gulp {
    mainClass = "dev.gulp.examples.topdown.TopdownGame"
    title = "Gulp Topdown"
}
