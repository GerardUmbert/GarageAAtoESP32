import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dunnowsoftware.GarageAAtoESP32"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dunnowsoftware.GarageAAtoESP32"
        minSdk = 29
        targetSdk = 35
        versionCode = 63
        versionName = "1.9.1"
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        create("release") {
            // Resolution order for each value:
            //   1. environment variable
            //   2. android/keystore.properties (gitignored, project-local)
            //   3. Gradle property (e.g. ~/.gradle/gradle.properties)
            //   4. default
            val keystorePropsFile = rootProject.file("keystore.properties")
            val keystoreProps = Properties().apply {
                if (keystorePropsFile.exists()) {
                    keystorePropsFile.inputStream().use { load(it) }
                }
            }
            fun secret(envName: String, propName: String): String? {
                val fromEnv: String? = System.getenv(envName)
                val fromFile: String? = keystoreProps.getProperty(propName)
                val fromGradle: String? = project.findProperty(propName) as String?
                return (fromEnv ?: fromFile ?: fromGradle)?.takeIf { it.isNotBlank() }
            }

            // Default: keystore lives at <repo-root>/.keystores/. rootDir is
            // the Gradle root (android/), so step up one level to the repo root.
            val storeFilePath = secret("KEYSTORE_PATH", "GARAGE_KEYSTORE_PATH")
                ?: "${rootDir.parentFile}/.keystores/garageaatoesp32.jks"
            val storePass = secret("KEYSTORE_PASSWORD", "GARAGE_KEYSTORE_PASSWORD")
            val keyAliasEnv = secret("KEY_ALIAS", "GARAGE_KEY_ALIAS") ?: "garageaatoesp32"
            val keyPass = secret("KEY_PASSWORD", "GARAGE_KEY_PASSWORD")

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
    implementation(libs.appcompat)
    implementation(libs.localbroadcastmanager)
    implementation(libs.car.app)
    implementation(libs.security.crypto)
    implementation(libs.coroutines.android)
    implementation(libs.preference.ktx)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.core.ktx)

    implementation(libs.play.services.location)
    implementation(libs.play.services.wearable)
    implementation(libs.osmdroid)
    implementation(libs.work.manager)

    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.foundation)
    debugImplementation(libs.compose.ui.tooling)
}
