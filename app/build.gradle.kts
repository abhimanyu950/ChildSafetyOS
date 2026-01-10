plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.childsafety.os"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.childsafety.os"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 🔴 CRITICAL FOR TENSORFLOW LITE (DO NOT REMOVE)
    aaptOptions {
        noCompress("tflite")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {

    /* -------- CORE ANDROID -------- */
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    /* -------- LIFECYCLE -------- */
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    /* -------- COMPOSE -------- */
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    /* -------- FIREBASE -------- */
    implementation("com.google.firebase:firebase-firestore-ktx:25.0.0")
    implementation("com.google.firebase:firebase-common-ktx:21.0.0")
    implementation("com.google.firebase:firebase-messaging-ktx:24.0.0")

    /* -------- TENSORFLOW LITE (PRODUCTION SAFE) -------- */
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    /* -------- TESTING -------- */
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    /* -------- DEBUG -------- */
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
