plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.garage.opener"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.garage.opener"
        minSdk = 29
        targetSdk = 35
        versionCode = 5
        versionName = "1.0.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.car.app)
    implementation(libs.security.crypto)
    implementation(libs.coroutines.android)
    implementation(libs.preference.ktx)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.core.ktx)
}
