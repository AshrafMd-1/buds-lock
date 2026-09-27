plugins {
    id("com.android.application")
}

android {
    namespace = "dev.ashraf.buds4readonly"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.ashraf.buds4readonly"
        minSdk = 24
        targetSdk = 35
        versionCode = 20
        versionName = "2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = false
        }
    }
}
