pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SafeSignal"

include(":detection", ":protocol", ":server", ":bot", ":kpi")
// :app and :overlay are added in M5, once the Android SDK is installed.
