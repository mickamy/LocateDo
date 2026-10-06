import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.locatedo.locatedo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.locatedo.locatedo"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    // One build type per server environment, matching the iOS Debug / Staging / Release configurations.
    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            // 10.0.2.2 is the emulator's alias for the host machine.
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080\"")
            buildConfigField("String", "APP_STATUS_URL", "\"https://locatedo.com/app-status-stg.json\"")
            buildConfigField("String", "REVENUECAT_API_KEY", "\"test_lqvPOSuItMaeQPmgcTBlMFVumra\"")
        }
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
            buildConfigField("String", "API_BASE_URL", "\"https://api.locatedo.com\"")
            buildConfigField("String", "APP_STATUS_URL", "\"https://locatedo.com/app-status.json\"")
            // The Play Store key is created in RevenueCat once the Play app exists.
            buildConfigField("String", "REVENUECAT_API_KEY", "\"\"")
        }
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".stg"
            matchingFallbacks += "release"
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("String", "API_BASE_URL", "\"https://api-stg.locatedo.com\"")
            buildConfigField("String", "APP_STATUS_URL", "\"https://locatedo.com/app-status-stg.json\"")
            buildConfigField("String", "REVENUECAT_API_KEY", "\"\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Connect clients and messages from shared/proto (`cd shared/proto && buf generate`).
    sourceSets.getByName("main") {
        java.directories.add("src/main/generated")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        warningsAsErrors = true
        // The string resources come from shared/strings; which ones a build uses is not lint's call.
        disable += "UnusedResources"
        // Version nudges would fail CI whenever something new ships; Dependabot handles updates.
        disable += listOf("NewerVersionAvailable", "AndroidGradlePluginVersion", "GradleDependency")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.connect.kotlin)
    implementation(libs.connect.kotlin.okhttp)
    implementation(libs.connect.kotlin.google.javalite.ext)
    implementation(libs.protobuf.javalite)
    implementation(libs.protobuf.kotlin.lite)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
