plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.robpelo.companion"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.robpelo.companion"
        minSdk = 30
        targetSdk = 34
        versionCode = 19
        versionName = "0.9.0-media-browser-routing"

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        aidl = true
    }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
    testImplementation("junit:junit:4.13.2")
}
