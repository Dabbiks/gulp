plugins {
    id("gulp.java-conventions")
    `java-gradle-plugin`
}

description = "Gradle plugin for game projects (id dev.gulp.game)."
group = "dev.gulp"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(libs.teavm.gradle.plugin)
    compileOnly(libs.jspecify)
}

// Plugins run inside the user's Gradle, which may use an older JDK than the framework.
tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

// Lets the plugin add the framework modules of its own version to game builds.
val writeVersion by tasks.registering {
    val output = layout.buildDirectory.file("generated/version/dev/gulp/gradle/version.txt")
    val versionText = project.version.toString()
    inputs.property("version", versionText)
    outputs.file(output)
    doLast { output.get().asFile.writeText(versionText) }
}
sourceSets.main {
    resources.srcDir(writeVersion.map { layout.buildDirectory.dir("generated/version").get() })
}

gradlePlugin {
    plugins {
        create("gulpGame") {
            id = "dev.gulp.game"
            implementationClass = "dev.gulp.gradle.GulpGamePlugin"
            displayName = "Gulp game"
            description = "Builds, runs and packages Gulp games for desktop and web."
        }
    }
}
