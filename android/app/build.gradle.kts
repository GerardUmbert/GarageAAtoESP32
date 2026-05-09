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
        versionCode = 6
        versionName = "1.0.5"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
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
