// The game engine: plain Kotlin with zero Android dependencies.
// Keeping it separate means the rules, strategy tables and probability maths
// can be unit-tested in milliseconds on the JVM, and could be reused on
// desktop, iOS (via Kotlin Multiplatform) or a web backend without change.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}

tasks.test {
    useJUnit()
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
