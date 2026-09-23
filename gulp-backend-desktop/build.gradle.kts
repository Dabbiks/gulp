plugins {
    id("gulp.java-conventions")
}

description = "Desktop backend on LWJGL 3: GLFW window, OpenGL 3.3 core."

val lwjglNatives = listOf(
    "natives-windows",
    "natives-windows-arm64",
    "natives-macos",
    "natives-macos-arm64",
    "natives-linux",
    "natives-linux-arm64",
)

dependencies {
    api(project(":gulp-core"))

    implementation(platform(libs.lwjgl.bom))
    implementation(libs.lwjgl.core)
    implementation(libs.lwjgl.glfw)
    implementation(libs.lwjgl.opengl)
    for (natives in lwjglNatives) {
        runtimeOnly(variantOf(libs.lwjgl.core) { classifier(natives) })
        runtimeOnly(variantOf(libs.lwjgl.glfw) { classifier(natives) })
        runtimeOnly(variantOf(libs.lwjgl.opengl) { classifier(natives) })
    }
}
