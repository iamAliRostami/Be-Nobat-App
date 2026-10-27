plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
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

        // Production server URL. Override via -PbackendUrl=... or local.properties.
        buildConfigField("String", "API_BASE_URL", "\"http://127.0.0.1:8080/api/v1\"")
        buildConfigField("String", "MEDIA_BASE_URL", "\"http://127.0.0.1:8080\"")
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
            // Talk to the dev server forwarded via `adb reverse tcp:8080 tcp:8080`.
            buildConfigField("String", "API_BASE_URL", "\"http://127.0.0.1:8080/api/v1\"")
            buildConfigField("String", "MEDIA_BASE_URL", "\"http://127.0.0.1:8080\"")
            isDebuggable = true
        }
        release {
            // Override these in your release signing config (e.g. via gradle.properties or CI env vars).
            buildConfigField("String", "API_BASE_URL", "\"https://api.benobat.example.com/api/v1\"")
            buildConfigField("String", "MEDIA_BASE_URL", "\"https://api.benobat.example.com\"")
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
    implementation("com.google.zxing:core:3.5.3")

    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}