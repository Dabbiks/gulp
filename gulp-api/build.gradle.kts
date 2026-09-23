plugins {
    id("gulp.api-conventions")
    id("gulp.coverage-conventions")
}

description = "Public game API: the only packages game code may import (dev.gulp.api.*)."

dependencies {
    api(libs.jspecify)
}
