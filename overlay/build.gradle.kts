plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.safesignal.overlay"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.safesignal.overlay"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}
