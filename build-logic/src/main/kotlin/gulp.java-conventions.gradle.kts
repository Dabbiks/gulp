// Base conventions for every Java module: Java 25 toolchain, strict javac, Spotless, JUnit, JaCoCo.

plugins {
    `java-library`
    jacoco
    id("com.diffplug.spotless")
}

val libs = the<VersionCatalogsExtension>().named("libs")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.findVersion("java").get().requiredVersion)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // "processing" only reports annotations no processor claims (e.g. JUnit @Test), which is noise with gulp-processor.
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing", "-Werror", "-parameters"))
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
}

dependencies {
    "testImplementation"(platform(libs.findLibrary("junit-bom").get()))
    "testImplementation"(libs.findLibrary("junit-jupiter").get())
    "testImplementation"(libs.findLibrary("assertj-core").get())
    "testRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
    finalizedBy(tasks.named("jacocoTestReport"))
}

jacoco {
    toolVersion = libs.findVersion("jacoco").get().requiredVersion
}

spotless {
    java {
        // Generated sources (GameAssets, dispatchers) are not formatted.
        targetExclude("build/**")
        palantirJavaFormat(libs.findVersion("palantir-java-format").get().requiredVersion)
        formatAnnotations()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
