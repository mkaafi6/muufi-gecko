plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mkaafi6.muufi"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mkaafi6.muufi"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        // Release signed with the auto-generated debug key (sideload-friendly).
    }

    buildTypes {
        release {
            // No R8: GeckoView is huge and minification saves little here;
            // avoids stripping needed classes/resources.
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs {
            // Store native libs compressed inside the APK (smaller download).
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.mozilla.geckoview:geckoview-omni:147.0.20260212191108")
}
