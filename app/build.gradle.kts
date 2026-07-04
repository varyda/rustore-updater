import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ru.app.rustoreupdater"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.app.rustoreupdater"
        minSdk = 24
        targetSdk = 35
        // Overridable from CI via -PversionCode / -PversionName gradle properties.
        versionCode = (project.findProperty("versionCode") as? String)?.toIntOrNull() ?: 3
        versionName = (project.findProperty("versionName") as? String) ?: "1.2"
        vectorDrawables { useSupportLibrary = true }
    }

    // Release signing key. In CI the keystore arrives as base64 in env vars
    // (KEYSTORE_BASE64 / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD), decoded
    // to a temp file by the workflow. Locally, where those vars are absent, we
    // fall back to the debug signing key so `assembleRelease` keeps working
    // without a keystore — only CI publishes properly signed builds.
    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("KEYSTORE_PATH")
            val storePassword = System.getenv("KEYSTORE_PASSWORD")
            val keyAlias = System.getenv("KEY_ALIAS")
            val keyPassword = System.getenv("KEY_PASSWORD")
            if (storeFilePath != null && File(storeFilePath).exists() &&
                storePassword != null && keyAlias != null && keyPassword != null
            ) {
                storeFile = File(storeFilePath)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
                println("release signing: using CI keystore at $storeFilePath")
            } else {
                println("release signing: CI keystore not found, falling back to debug signing")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use the CI release key when present; otherwise fall back to the
            // debug key so local `assembleRelease` still produces an installable APK.
            signingConfig = signingConfigs.getByName("release").storeFile
                ?.let { signingConfigs.getByName("release") }
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Image loading
    implementation(libs.coil.compose)

    // Immutable collections — gives Compose a stable List type so feed sections are skippable.
    implementation(libs.kotlinx.collections.immutable)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Material components (for theme base)
    implementation(libs.material)
}
