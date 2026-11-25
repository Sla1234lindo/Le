plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.potato.demo"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.potato.demo"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "21.0-alpha"
    }
}

dependencies { implementation(project(":potatoai")) }