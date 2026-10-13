plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass = "com.safesignal.server.ApplicationKt"
}

dependencies {
    implementation(project(":protocol"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}
