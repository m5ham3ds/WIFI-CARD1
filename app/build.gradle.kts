import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.aistudio.wifimasterpro.qvnkuq"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val customDebugKeystore = file("${rootDir}/debug.keystore")
        if (customDebugKeystore.exists()) {
            create("debugConfig") {
                storeFile = customDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            val customDebug = signingConfigs.findByName("debugConfig")
            if (customDebug != null) {
                signingConfig = customDebug
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = false
        checkReleaseBuilds = false
    }
    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
        getByName("test").assets.srcDirs("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.koin.android)
    implementation(libs.koin.core)
    implementation(libs.coroutines.android)
    implementation(libs.timber)
    implementation(libs.security.crypto)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.preference.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.junit.ext)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.room.testing)
}

afterEvaluate {
    tasks.named("assembleDebug").configure {
        doLast {
            val debugDir = layout.buildDirectory.dir("outputs/apk/debug").get().asFile
            val originalApk = File(debugDir, "app-debug.apk")
            if (originalApk.exists()) {
                originalApk.copyTo(File(debugDir, "WIFI-CARD.apk"), overwrite = true)
            }
        }
    }
}
