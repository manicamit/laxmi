import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.laxmi.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.laxmi.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"

        // Mission-layer key: read from gitignored local.properties (laxmi.geminiApiKey=...)
        // or the GEMINI_API_KEY env var. Never hardcode, never commit.
        val props = Properties()
        val propsFile = rootProject.file("local.properties")
        if (propsFile.exists()) {
            propsFile.inputStream().use { stream -> props.load(stream) }
        }
        val geminiKey: String = props.getProperty("laxmi.geminiApiKey")
            ?: System.getenv("GEMINI_API_KEY") ?: ""
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // On-device Gemma inference — LiteRT-LM (successor to mediapipe tasks-genai,
    // which is now maintenance-only). If this coordinate fails to resolve, check
    // https://developers.google.com/edge/litert-lm/android for the current artifact.
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
