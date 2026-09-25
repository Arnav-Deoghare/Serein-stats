import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

// Release signing credentials come from keystore.properties locally (gitignored,
// see keystore.properties.example) or from environment variables in CI. Neither
// being present is fine for everyday debug builds — only `assembleRelease` needs them.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}
fun signingProp(key: String, env: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(env)

android {
    namespace = "com.serein.stats"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.serein.stats"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            signingProp("storeFile", "SEREIN_STORE_FILE")?.let { storeFile = file(it) }
            storePassword = signingProp("storePassword", "SEREIN_STORE_PASSWORD")
            keyAlias      = signingProp("keyAlias", "SEREIN_KEY_ALIAS")
            keyPassword   = signingProp("keyPassword", "SEREIN_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.appcompat)
    implementation(libs.work.runtime.ktx)
    ksp(libs.room.compiler)
}