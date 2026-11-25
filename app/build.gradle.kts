import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing: keystore.properties locally, environment variables on GitHub Actions.
val keyProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(prop: String, env: String): String? = keyProps.getProperty(prop) ?: System.getenv(env)

// GitHub repository used for update checks ("owner/name"); override with -PupdateRepo=... or UPDATE_REPO.
val updateRepo: String = (findProperty("updateRepo") as String?) ?: System.getenv("UPDATE_REPO") ?: "Galis228/MaterialEReader"

android {
    namespace = "io.github.galis228.materialereader"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.galis228.materialereader"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        create("release") {
            val store = signingValue("storeFile", "KEYSTORE_FILE")
            if (store != null) {
                storeFile = file(store)
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val rel = signingConfigs.getByName("release")
            if (rel.storeFile != null) signingConfig = rel
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
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs { useLegacyPackaging = false }
    }
    lint { checkReleaseBuilds = false }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.03.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jsoup:jsoup:1.20.1")
    implementation("com.github.junrar:junrar:7.5.5")
    // ИИ, как в НаноПеро: Gemini Nano (AICore) и модели .litertlm (Gemma 4, Qwen 3); GGUF — через libnanollama.so
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta5")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.19.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
