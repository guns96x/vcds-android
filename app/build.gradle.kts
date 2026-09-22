plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vag.vcdsandroid"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vag.vcdsandroid"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
        viewBinding = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    // Encrypted storage for the GitHub token: it is a credential,
    // so it never goes into plain preferences or into the apk.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // USB Serial library for FTDI, CH340, CP2102, Prolific over USB-OTG
    implementation("com.github.mik3y:usb-serial-for-android:3.8.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Unit Testing
    testImplementation("junit:junit:4.13.2")
}
