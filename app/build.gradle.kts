plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.symbol.processing.gradle)
    id("androidx.navigation.safeargs.kotlin")
}
kotlin {
    jvmToolchain(17)
}
android {
    namespace = "net.dacworld.android.holyplacesofthelord"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.dacworld.android.holyplacesofthelord"
        minSdk = 24
        targetSdk = 36
        versionCode = 6
        versionName = "1.2"

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
        // Flag to enable support for newer Java language features.
        isCoreLibraryDesugaringEnabled = true
        // Set Java version compatibility.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.material)

    implementation(libs.coil)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    // Optional - Kotlin Extensions and Coroutines support for Room
    implementation(libs.androidx.room.ktx)

    // Optional - Test helpers
    testImplementation(libs.androidx.room.testing)

    // MapLibre GL Native
    implementation(libs.android.sdk)
    implementation(libs.android.plugin.annotation.v9)
    implementation(libs.android.plugin.markerview.v9)
    implementation(libs.mapbox.sdk.geojson)

    // Location Services
    implementation(libs.google.play.services.location)

    // Data Store Preferences
    implementation(libs.androidx.datastore.preferences)

    // ... other dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}