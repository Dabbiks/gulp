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
    implementation(libs.lwjgl.stb)
    for (natives in lwjglNatives) {
        runtimeOnly(variantOf(libs.lwjgl.core) { classifier(natives) })
        runtimeOnly(variantOf(libs.lwjgl.glfw) { classifier(natives) })
        runtimeOnly(variantOf(libs.lwjgl.opengl) { classifier(natives) })
        runtimeOnly(variantOf(libs.lwjgl.stb) { classifier(natives) })
    }
}

// Visual tests need an OpenGL window, so `check` skips them; CI runs them under Xvfb.
tasks.named<Test>("test") {
    useJUnitPlatform { excludeTags("visual") }
}

val visualTest by tasks.registering(Test::class) {
    description = "Renders scenes in a real window and compares them with reference images."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("visual") }
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    if (System.getProperty("os.name").startsWith("Mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
    systemProperty("gulp.visual.references", file("src/test/resources/visual").path)
    systemProperty("gulp.visual.output", layout.buildDirectory.dir("visual").get().asFile.path)
    systemProperty("gulp.visual.update", providers.gradleProperty("gulp.updateReferences").isPresent)
    outputs.upToDateWhen { false }
}
