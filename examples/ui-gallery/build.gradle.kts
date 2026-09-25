plugins {
    id("gulp.java-conventions")
    id("dev.gulp.game")
}

description = "UI gallery: every container and widget, usable with mouse, keyboard, touch and gamepad alone."

dependencies {
    implementation(project(":gulp-api"))
    annotationProcessor(project(":gulp-processor"))
    desktopRuntime(project(":gulp-backend-desktop"))
    webRuntime(project(":gulp-backend-web"))
    gulpTools(project(":gulp-tools"))
}

gulp {
    mainClass = "dev.gulp.examples.gallery.UiGalleryGame"
    title = "Gulp UI Gallery"
}
