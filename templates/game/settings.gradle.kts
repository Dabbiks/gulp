// Until Gulp is published, a game builds against a Gulp checkout: -Pgulp.home=/path/to/gulp in the command line or in
// gradle.properties (by default ../gulp). Gradle then builds the plugin and the engine from source, once.
pluginManagement {
    val gulpHome = providers.gradleProperty("gulp.home").getOrElse("../gulp")
    includeBuild("$gulpHome/gulp-gradle-plugin")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "mygame"

includeBuild(providers.gradleProperty("gulp.home").getOrElse("../gulp"))
