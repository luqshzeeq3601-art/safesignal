// Java-only module: pure maths, no Android or Kotlin types.
plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    testImplementation(libs.junit)
}
