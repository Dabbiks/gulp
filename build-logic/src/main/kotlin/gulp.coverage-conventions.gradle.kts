// Enforces the "at least 80% logic coverage" rule from the definition of done (spec, section 1).
// Applied to modules whose logic is testable on the headless backend.

plugins {
    id("gulp.java-conventions")
}

val coverageVerification = tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
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
