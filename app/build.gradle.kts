plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.symbol.processing.gradle)
}

android {
    namespace = "net.dacworld.android.holyplacesofthelord"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.dacworld.android.holyplacesofthelord"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

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
        sourceCompatibility = JavaVersion.VERSION_1_8 // Or higher if needed
        targetCompatibility = JavaVersion.VERSION_1_8 // Or higher if needed
    }
    buildFeatures {
        viewBinding = true
    }
}
kotlin {
    jvmToolchain(11)
}

dependencies {
    dependencies {

        coreLibraryDesugaring(libs.desugar.jdk.libs)

        implementation(libs.androidx.room.runtime)
        ksp(libs.androidx.room.compiler)

        // Optional - Kotlin Extensions and Coroutines support for Room
        implementation(libs.androidx.room.ktx)

        // Optional - Test helpers
        testImplementation(libs.androidx.room.testing)

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
    }
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