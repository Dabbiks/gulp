plugins {
    id("gulp.java-conventions")
}

description = "CLI tools: atlas packer, MSDF and bitmap font generator, importers."

val lwjglNatives = listOf(
    "natives-windows",
    "natives-windows-arm64",
    "natives-macos",
    "natives-macos-arm64",
    "natives-linux",
    "natives-linux-arm64",
)

dependencies {
    implementation(project(":gulp-core"))
    implementation(platform(libs.lwjgl.bom))
    implementation(libs.lwjgl.core)
    implementation(libs.lwjgl.msdfgen)
    implementation(libs.lwjgl.freetype)
    for (natives in lwjglNatives) {
        runtimeOnly(variantOf(libs.lwjgl.core) { classifier(natives) })
        runtimeOnly(variantOf(libs.lwjgl.msdfgen) { classifier(natives) })
        runtimeOnly(variantOf(libs.lwjgl.freetype) { classifier(natives) })
    }
}

// The engine's built-in font (Fira Sans, SIL OFL 1.1), generated once and kept in gulp-core's resources:
// ./gradlew :gulp-tools:generateDefaultFont
tasks.register<JavaExec>("generateDefaultFont") {
    description = "Regenerates the default MSDF font in gulp-core/src/main/resources/assets/gulp/fonts."
    group = "gulp"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.gulp.tools.GulpTools"
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Djava.awt.headless=true")
    val output = rootProject.file("gulp-core/src/main/resources/assets/gulp/fonts")
    args("msdf", file("fonts/FiraSans-Regular.ttf").path, output.path, "default", "32", "8")
    val license = file("fonts/OFL.txt")
    doLast { license.copyTo(output.resolve("default-OFL.txt"), overwrite = true) }
}
