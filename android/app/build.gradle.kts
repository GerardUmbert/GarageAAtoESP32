plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.dunnowsoftware.GarageAAtoESP32"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dunnowsoftware.GarageAAtoESP32"
        minSdk = 29
        targetSdk = 35
        versionCode = 9
        versionName = "1.0.8"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("KEYSTORE_PATH")
                ?: "${rootDir}/../.keystores/garageaatoesp32.jks"
            val storePass = System.getenv("KEYSTORE_PASSWORD")
            val keyAliasEnv = System.getenv("KEY_ALIAS") ?: "garageaatoesp32"
            val keyPass = System.getenv("KEY_PASSWORD")

            if (storePass != null && keyPass != null && file(storeFilePath).exists()) {
                storeFile = file(storeFilePath)
                storePassword = storePass
                keyAlias = keyAliasEnv
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("release")
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
