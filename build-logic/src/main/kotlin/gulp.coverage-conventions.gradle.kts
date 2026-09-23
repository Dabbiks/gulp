// Enforces the "at least 80% logic coverage" rule from the definition of done (spec, section 1).
// Applied to modules whose logic is testable on the headless backend.
//
// Coverage counts every test suite of these modules (ADR 0005): API behaviour such as GameModule.require or the Owner
// shortcuts can only be exercised with a running engine, so those tests live in gulp-core and still cover gulp-api.

plugins {
    id("gulp.java-conventions")
}

val coverageModules = listOf("gulp-api", "gulp-core", "gulp-backend-headless")

val coverageVerification = tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    for (module in coverageModules) {
        dependsOn(":$module:test")
        executionData(rootProject.layout.projectDirectory.file("$module/build/jacoco/test.exec"))
    }
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(coverageVerification)
}

tasks.named<JacocoReport>("jacocoTestReport") {
    for (module in coverageModules) {
        dependsOn(":$module:test")
        executionData(rootProject.layout.projectDirectory.file("$module/build/jacoco/test.exec"))
    }
    reports {
        csv.required = true
    }
}
