import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.contextos.core.service"
    compileSdk = 34

    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        
        val apiKey = localProperties.getProperty("OPENCLAW_API_KEY", "")
        val isGroqKey = apiKey.startsWith("gsk_")
        val defaultModel = if (isGroqKey) "openai/gpt-oss-120b" else "qwen/qwen3-32b"
        val apiEndpoint = if (isGroqKey) "https://api.groq.com/openai/v1" else "https://generativelanguage.googleapis.com/v1beta/models"
        
        buildConfigField("String", "OPENCLAW_API_KEY", "\"$apiKey\"")
        buildConfigField("String", "OPENCLAW_API_ENDPOINT", "\"$apiEndpoint\"")
        buildConfigField("String", "OPENCLAW_API_PROVIDER", "\"${if (isGroqKey) "groq" else "gemini"}\"")
        buildConfigField("boolean", "OPENCLAW_ENABLE_REASONING", localProperties.getProperty("OPENCLAW_ENABLE_REASONING", "true"))
        buildConfigField("boolean", "OPENCLAW_ENABLE_DRAFTING", localProperties.getProperty("OPENCLAW_ENABLE_DRAFTING", "true"))
        buildConfigField("String", "OPENCLAW_REASONING_MODEL", "\"${localProperties.getProperty("OPENCLAW_REASONING_MODEL", defaultModel)}\"")
        buildConfigField("String", "OPENCLAW_DRAFTING_MODEL", "\"${localProperties.getProperty("OPENCLAW_DRAFTING_MODEL", defaultModel)}\"")
        buildConfigField("String", "OPENCLAW_CHAT_MODEL", "\"${localProperties.getProperty("OPENCLAW_CHAT_MODEL", defaultModel)}\"")
        buildConfigField("boolean", "OPENCLAW_USE_MOCK", localProperties.getProperty("OPENCLAW_USE_MOCK", "false"))
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    // Core
    implementation(libs.androidx.core.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Kotlin Serialization
    implementation(libs.kotlinx.serialization.json)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // WorkManager + Hilt-Work integration
    implementation(libs.workmanager.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)

    // Google Play Services
    implementation(libs.play.services.location)

    // Project modules
    implementation(project(":core:data"))
    implementation(project(":core:skills"))
    implementation(project(":core:memory"))
    implementation(project(":core:network"))

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.android.compiler)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)
}
