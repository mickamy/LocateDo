import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.secrets)
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
        // The secrets plugin fills this per app variant; the unit-test manifest merge only sees this default.
        manifestPlaceholders["MAPS_API_KEY"] = ""
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
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric's API 36 image reaches into FileDescriptor internals that the JDK hides from other modules.
        unitTests.all { it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED") }
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

room {
    schemaDirectory("$projectDir/schemas")
}

// MAPS_API_KEY comes from local.properties (not committed); CI and clean checkouts fall back to the defaults file.
secrets {
    defaultPropertiesFileName = "local.defaults.properties"
    ignoreList.add("sdk.*")
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
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.places)
    implementation(libs.connect.kotlin)
    implementation(libs.connect.kotlin.okhttp)
    implementation(libs.connect.kotlin.google.javalite.ext)
    implementation(libs.protobuf.javalite)
    implementation(libs.protobuf.kotlin.lite)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}
