plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.onetaskman.student"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.onetaskman.student"
        minSdk = 26 // Android 8.0 [cite: 135, 144]
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        viewBinding = true // Enables easy UI reference [cite: 144]
    }
}

kotlin {
    jvmToolchain(8)
}

dependencies {
    // Firebase - Use the BoM (Bill of Materials) to manage versions [cite: 144]
    implementation(platform("com.google.firebase:firebase-bom:33.0.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-functions-ktx") // Added for Cloud Functions

    // UI Components [cite: 144]
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.12.0")

    // Coroutines (replaces asyncio from the Python version) [cite: 144]
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Standard Libraries [cite: 144]
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Gson for JSON serialization
    implementation("com.google.code.gson:gson:2.10.1")
}