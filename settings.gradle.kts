pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PocketAI"
include(":app")
include(":llama-android-lib")
project(":llama-android-lib").projectDir = file("work/llama.cpp/examples/llama.android/lib")
