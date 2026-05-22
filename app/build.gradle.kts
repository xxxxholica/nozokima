plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val isWindows = System.getProperty("os.name").lowercase().contains("win")

if (isWindows) {
    val tmpDir = rootProject.layout.projectDirectory.dir(".gradle/tmp").asFile
    try {
        if (!tmpDir.exists()) tmpDir.mkdirs()
        if (tmpDir.exists() && tmpDir.canWrite()) {
            System.setProperty("java.io.tmpdir", tmpDir.absolutePath)
        }
    } catch (e: Exception) {
    }
}

android {
    namespace = "com.example.nozokima"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.nozokima"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
    // assetsに巨大ファイルを置かないため、noCompressの設定は不要になりました
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("net.objecthunter:exp4j:0.4.8")

    // ML Kit Text Recognition
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // ML Kit GenAI Prompt API (Gemini Nano)
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta2")

    // Biometric
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    implementation("net.lingala.zip4j:zip4j:2.11.5")
    implementation("io.coil-kt:coil-compose:2.6.0")
}