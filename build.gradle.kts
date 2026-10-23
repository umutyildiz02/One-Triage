plugins {
    id("com.android.application") version "8.13.2" apply false
    kotlin("android") version "1.9.25" apply false
    kotlin("jvm") version "1.9.25" apply false
}

// Print aggregate counts directly from the running test task; no sensitive test values.
subprojects {
    tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
        addTestListener(object : org.gradle.api.tasks.testing.TestListener {
            override fun beforeSuite(suite: org.gradle.api.tasks.testing.TestDescriptor) {}
            override fun beforeTest(test: org.gradle.api.tasks.testing.TestDescriptor) {}
            override fun afterTest(test: org.gradle.api.tasks.testing.TestDescriptor, result: org.gradle.api.tasks.testing.TestResult) {}
            override fun afterSuite(suite: org.gradle.api.tasks.testing.TestDescriptor, result: org.gradle.api.tasks.testing.TestResult) {
                if (suite.parent == null) logger.lifecycle("TEST_TOTAL $path: ${result.testCount} tests, ${result.successfulTestCount} passed, ${result.failedTestCount} failed, ${result.skippedTestCount} skipped")
            }
        })
    }
}
