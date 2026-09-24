plugins {
    id("gulp.java-conventions")
    id("dev.gulp.game")
}

description = "Physics sandbox: stacks, a pyramid, a chain bridge, a pendulum, a car and balls to drag around."

dependencies {
    implementation(project(":gulp-api"))
    annotationProcessor(project(":gulp-processor"))
    desktopRuntime(project(":gulp-backend-desktop"))
    webRuntime(project(":gulp-backend-web"))
    gulpTools(project(":gulp-tools"))
}

gulp {
    mainClass = "dev.gulp.examples.sandbox.SandboxGame"
    title = "Gulp Sandbox"
}
