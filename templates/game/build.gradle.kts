plugins {
    id("dev.gulp.game")
}

group = "com.example"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

val gulpVersion = "0.1.0-SNAPSHOT"

dependencies {
    implementation("dev.gulp:gulp-api:$gulpVersion")
    annotationProcessor("dev.gulp:gulp-processor:$gulpVersion")

    testImplementation("dev.gulp:gulp-backend-headless:$gulpVersion")
    testAnnotationProcessor("dev.gulp:gulp-processor:$gulpVersion")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

gulp {
    mainClass = "com.example.mygame.MyGame"
    title = "My Game"
    desktop {
        vendor = "Example"
    }
}
