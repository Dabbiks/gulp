pluginManagement {
    includeBuild("build-logic")
    includeBuild("gulp-gradle-plugin")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "gulp"

include(
    "gulp-api",
    "gulp-platform",
    "gulp-core",
    "gulp-backend-desktop",
    "gulp-backend-web",
    "gulp-backend-headless",
    "gulp-processor",
    "gulp-test",
    "gulp-tools",
)

include("examples:showcase")
