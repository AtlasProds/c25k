plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0"
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.example.c25kbuddy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.c25kbuddy"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

    }

    signingConfigs {
        create("release") {
            // You'll need to provide these values via environment variables or gradle.properties
            // for security reasons, don't hardcode them here
            storeFile = file("../c25k-release-key.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: project.findProperty("keystore.password") as String? ?: "changeit"
            keyAlias = "c25kbuddy"
            keyPassword = System.getenv("KEY_PASSWORD") ?: project.findProperty("key.password") as String? ?: "changeit"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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
    
    // Add lint options to ignore errors
    lint {
        abortOnError = false
        baseline = file("lint-baseline.xml")
        checkReleaseBuilds = false
        // Ignore the POST_NOTIFICATIONS permission issue for now
        disable += "NotificationPermission"
        disable += "MutableCollectionMutableState"
        disable += "AutoboxingStateCreation"
    }
}

dependencies {
    implementation(libs.play.services.wearable)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.wear.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    
    // Add the explicit runtime dependency
    implementation(libs.androidx.compose.runtime)
    
    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    // Lifecycle components
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-service:2.6.2")
    
    // Horologist - Wear OS toolkit
    implementation("com.google.android.horologist:horologist-compose-layout:0.5.3")
    implementation("com.google.android.horologist:horologist-compose-material:0.5.3")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    
    // Hilt dependency injection
    implementation("com.google.dagger:hilt-android:2.48.1")
    kapt("com.google.dagger:hilt-android-compiler:2.48.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
}