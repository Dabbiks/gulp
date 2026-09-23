plugins {
    id("gulp.java-conventions")
}

description = "CLI tools: atlas packer, MSDF and bitmap font generator, importers."

dependencies {
    implementation(project(":gulp-core"))
}
