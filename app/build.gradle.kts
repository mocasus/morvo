import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.morvo"
    compileSdk = 34

    // Read signing config from signing.properties (CI-generated)
    val signingProps = Properties()
    val signingFile = rootProject.file("app/signing.properties")
    if (signingFile.exists()) {
        signingProps.load(FileInputStream(signingFile))
    }

    signingConfigs {
        create("release") {
            if (signingFile.exists()) {
                storeFile = rootProject.file("app/" + (signingProps.getProperty("storeFile") ?: "morvo.keystore"))
                storePassword = signingProps.getProperty("storePassword") ?: "android"
                keyAlias = signingProps.getProperty("keyAlias") ?: "morvo"
                keyPassword = signingProps.getProperty("keyPassword") ?: "android"
            }
        }
    }

    defaultConfig {
        applicationId = "com.morvo"
        minSdk = 28
        targetSdk = 34
        versionCode = 3
        versionName = "0.7.1"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Link native sources from project root
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("libs")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}