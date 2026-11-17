import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val propFile = rootProject.file("local.properties")
    if (propFile.exists()) {
        propFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.leon.be_nobat"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.leon.be_nobat"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Default backend URL. Override via -PbackendUrl=... or local.properties.
        val defaultBackendUrl = (project.findProperty("backendUrl") as? String)?.trimEnd('/')
            ?: "https://benobat.ir"
        buildConfigField("String", "API_BASE_URL", "\"$defaultBackendUrl/api/v1\"")
        buildConfigField("String", "MEDIA_BASE_URL", "\"$defaultBackendUrl\"")
        buildConfigField("String", "API_VERSION", "\"v1\"")
    }

    flavorDimensions += "version"
    productFlavors {
        create("prod") {
            dimension = "version"
            buildConfigField("Boolean", "DEMO_MODE", "false")
        }
        create("demo") {
            dimension = "version"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            buildConfigField("Boolean", "DEMO_MODE", "true")
        }
    }

    buildTypes {
        debug {
            // Local development URL. Priority:
            // 1. -PbackendUrl=... command-line argument
            // 2. backendUrl in local.properties
            // 3. Default: http://127.0.0.1:8080 (auto-mapped to 10.0.2.2 on emulator by BeNobatApiConfig)
            val debugBackendUrl = (project.findProperty("backendUrl") as? String)?.trimEnd('/')
                ?: localProperties.getProperty("backendUrl")?.trimEnd('/')
                ?: "http://127.0.0.1:8080"

            buildConfigField("String", "API_BASE_URL", "\"$debugBackendUrl/api/v1\"")
            buildConfigField("String", "MEDIA_BASE_URL", "\"$debugBackendUrl\"")
            isDebuggable = true
        }
        release {
            // Production URL: https://benobat.ir. Priority:
            // 1. -PbackendUrl=... command-line argument
            // 2. backendUrlRelease in local.properties
            // 3. Default: https://benobat.ir
            val releaseBackendUrl = (project.findProperty("backendUrl") as? String)?.trimEnd('/')
                ?: localProperties.getProperty("backendUrlRelease")?.trimEnd('/')
                ?: "https://benobat.ir"

            buildConfigField("String", "API_BASE_URL", "\"$releaseBackendUrl/api/v1\"")
            buildConfigField("String", "MEDIA_BASE_URL", "\"$releaseBackendUrl\"")
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.exifinterface)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.cardview)

    // Networking
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.datetime)

    // Storage / Security
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.splashscreen)

    // DI
    implementation(libs.koin.android)

    // Lifecycle / ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Image loading
    implementation(libs.coil)
    implementation(libs.coil.svg)
    implementation(libs.core)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}