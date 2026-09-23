plugins {
    id("gulp.coverage-conventions")
}

description = "Implementation of the API on top of the platform SPI. Must compile and run under TeaVM."

dependencies {
    api(project(":gulp-api"))
    api(project(":gulp-platform"))

    testImplementation(project(":gulp-backend-headless"))
}
