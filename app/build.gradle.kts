plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt") // Ensure this is applied
}

android {
    namespace = "id.frogobox.compliervx"
    compileSdk = 34

    defaultConfig {
        applicationId = "id.frogobox.compliervx"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // New compilerOptions DSL for Kotlin
    kotlin {
        jvmToolchain(17) // Use JVM 17 for Kotlin compilation
        compilerOptions {
            jvmTarget.set("17") // Correct way to set JVM target
        }
    }

    buildFeatures {
        dataBinding = true
        viewBinding = true
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Kapt libraries if you are using them (e.g., Room, Dagger, Data Binding)
    // Example for Room:
    // kapt("androidx.room:room-compiler:2.6.1")
    // Example for Dagger Hilt:
    // kapt("com.google.dagger:hilt-compiler:2.51")

}