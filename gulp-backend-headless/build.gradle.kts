plugins {
    id("gulp.coverage-conventions")
}

description = "Backend without window or sound, driven manually. Used by tests and CI."

dependencies {
    api(project(":gulp-core"))
}
