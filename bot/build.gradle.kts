plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass = "com.safesignal.bot.BotMainKt"
}

dependencies {
    implementation(project(":protocol"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}
