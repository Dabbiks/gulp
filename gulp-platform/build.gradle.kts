plugins {
    id("gulp.api-conventions")
}

description = "Platform SPI implemented by every backend; visible only to gulp-core and backends."

dependencies {
    api(libs.jspecify)
}
