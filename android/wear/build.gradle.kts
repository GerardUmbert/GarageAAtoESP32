import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dunnowsoftware.GarageAAtoESP32"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dunnowsoftware.GarageAAtoESP32"
        minSdk = 26
        targetSdk = 36
        versionCode = 10081
        versionName = "1.12.8"
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        create("release") {
            val keystorePropsFile = rootProject.file("keystore.properties")
            val keystoreProps = Properties().apply {
                if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
            }
            fun secret(envName: String, propName: String): String? {
                val fromEnv: String? = System.getenv(envName)
                val fromFile: String? = keystoreProps.getProperty(propName)
                val fromGradle: String? = project.findProperty(propName) as String?
                return (fromEnv ?: fromFile ?: fromGradle)?.takeIf { it.isNotBlank() }
            }
            val storeFilePath = secret("KEYSTORE_PATH", "GARAGE_KEYSTORE_PATH")
                ?: "${rootDir.parentFile}/.keystores/garageaatoesp32.jks"
            val storePass = secret("KEYSTORE_PASSWORD", "GARAGE_KEYSTORE_PASSWORD")
            val keyAliasVal = secret("KEY_ALIAS", "GARAGE_KEY_ALIAS") ?: "garageaatoesp32"
            val keyPass = secret("KEY_PASSWORD", "GARAGE_KEY_PASSWORD")
            if (storePass != null && keyPass != null && file(storeFilePath).exists()) {
                storeFile = file(storeFilePath)
                storePassword = storePass
                keyAlias = keyAliasVal
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
    implementation(libs.wear.compose)
    implementation(libs.wear.compose.foundation)
    implementation(libs.concurrent.futures)
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.protolayout.material)
    implementation(libs.wear.protolayout.expression)
    implementation(libs.wear.tiles.material)
    implementation(libs.play.services.wearable)
    implementation(libs.wear.input)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.playservices)
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
}
