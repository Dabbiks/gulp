plugins {
    id("gulp.java-conventions")
}

description = "Testing tools for games: GameTestHarness, input simulation, ticking, snapshots."

dependencies {
    api(project(":gulp-core"))
    api(project(":gulp-backend-headless"))
}
