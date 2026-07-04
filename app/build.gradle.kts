import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
}

val isWindows = System.getProperty("os.name").lowercase().contains("win")

if (isWindows) {
    val tmpDir = rootProject.layout.projectDirectory.dir(".gradle/tmp").asFile
    try {
        if (!tmpDir.exists()) tmpDir.mkdirs()
        if (tmpDir.exists() && tmpDir.canWrite()) {
            System.setProperty("java.io.tmpdir", tmpDir.absolutePath)
        }
    } catch (_: Exception) {
    }
}

extensions.configure<ApplicationExtension> {
    namespace = "works.hinata.nozokima"
    compileSdk = 37

    defaultConfig {
        applicationId = "works.hinata.nozokima"
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

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ML Kit Text Recognition
    implementation(libs.mlkit.text.japanese)
    implementation(libs.kotlinx.coroutines.play.services)

    // ML Kit GenAI Prompt API (Gemini Nano)
    implementation(libs.mlkit.genai.prompt)

    // Biometric
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.zip4j)
    implementation(libs.coil.compose)
}
