plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Versi diisi otomatis oleh GitHub Actions (run_number & tanggal build).
val ciVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
val ciVersionName = System.getenv("VERSION_NAME") ?: "dev"

// Repo publik tempat GitHub Releases & remote config berada.
val githubRepo = System.getenv("GITHUB_REPOSITORY")?.takeIf { it.isNotBlank() } ?: "wahyusubrata10/dietapp"

val keystorePath: String? = System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotBlank() && file(it).exists() }

android {
    namespace = "com.hawdietpro.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hawdietpro.app"
        minSdk = 26
        targetSdk = 35
        versionCode = ciVersionCode
        versionName = ciVersionName

        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")
        buildConfigField(
            "String",
            "REMOTE_BASE_URL",
            "\"https://raw.githubusercontent.com/$githubRepo/main/remote/\"",
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        // Snapshot UI (Robolectric) butuh resource Android (font, tema).
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            test.systemProperty("snapshotDir", layout.buildDirectory.dir("snapshots").get().asFile.absolutePath)
            test.maxHeapSize = "3g"
            test.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

/**
 * Menyalin isi folder /remote (sumber tunggal remote config) ke assets/remote,
 * sehingga APK selalu membawa salinan bawaan untuk dipakai saat offline.
 */
abstract class CopyRemoteConfigTask : DefaultTask() {
    @get:InputDirectory
    abstract val sourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = outputDir.get().asFile.resolve("remote")
        target.deleteRecursively()
        target.mkdirs()
        sourceDir.get().asFile.listFiles { f -> f.extension == "json" }?.forEach {
            it.copyTo(target.resolve(it.name), overwrite = true)
        }
    }
}

val copyRemoteConfig = tasks.register<CopyRemoteConfigTask>("copyRemoteConfig") {
    sourceDir.set(rootProject.layout.projectDirectory.dir("remote"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyRemoteConfig, CopyRemoteConfigTask::outputDir)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    // Widget layar utama.
    implementation(libs.androidx.glance.appwidget)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // Pemindai barcode offline (model tertanam di APK, tanpa Google Play Services / internet).
    implementation(libs.mlkit.barcode.scanning)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Snapshot tampilan (hanya varian debug): render Compose ke PNG tanpa emulator.
    testDebugImplementation(libs.robolectric)
    testDebugImplementation(platform(libs.androidx.compose.bom))
    testDebugImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
