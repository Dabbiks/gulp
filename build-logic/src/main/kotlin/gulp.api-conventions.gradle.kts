// Conventions for public contract modules (gulp-api, gulp-platform):
// every public and protected element must have Javadoc, enforced by javac doclint + -Werror.

plugins {
    id("gulp.java-conventions")
}

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.add("-Xdoclint:all/protected")
}
