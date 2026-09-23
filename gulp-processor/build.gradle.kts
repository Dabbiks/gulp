plugins {
    id("gulp.java-conventions")
}

description = "Annotation processor: generated event dispatch, component codecs, module validation. Implemented in stage 1."

dependencies {
    compileOnly(project(":gulp-api"))
}

dependencies {
    // Compilation tests need the API and JSpecify on the test classpath.
    testImplementation(project(":gulp-api"))
}
