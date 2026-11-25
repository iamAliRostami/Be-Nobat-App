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
        versionCode = 1
        versionName = "1.0.0"
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
            val debugBackendUrl = (project.findProperty("backendUrl") as? String)?.trimEnd('/')
                ?: localProperties.getProperty("backendUrl")?.trimEnd('/')
                ?: "http://127.0.0.1:5000"

            buildConfigField("String", "API_BASE_URL", "\"$debugBackendUrl/api/v1\"")
            buildConfigField("String", "MEDIA_BASE_URL", "\"$debugBackendUrl\"")
            isDebuggable = true
        }
        release {
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
        create("releaseHuggingFace") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            val url = (project.findProperty("backendUrlHuggingFace") as? String)?.trimEnd('/')
                ?: localProperties.getProperty("backendUrlHuggingFace")?.trimEnd('/')
                ?: "https://benobat.hf.space"

            buildConfigField("String", "API_BASE_URL", "\"$url/api/v1\"")
            buildConfigField("String", "MEDIA_BASE_URL", "\"$url\"")
        }
        create("releaseLiara") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            val url = (project.findProperty("backendUrlLiara") as? String)?.trimEnd('/')
                ?: localProperties.getProperty("backendUrlLiara")?.trimEnd('/')
                ?: "https://benobat.liara.run"

            buildConfigField("String", "API_BASE_URL", "\"$url/api/v1\"")
            buildConfigField("String", "MEDIA_BASE_URL", "\"$url\"")
        }
        create("releaseChabokan") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            val url = (project.findProperty("backendUrlChabokan") as? String)?.trimEnd('/')
                ?: localProperties.getProperty("backendUrlChabokan")?.trimEnd('/')
                ?: "https://benobat.chabokan.net"

            buildConfigField("String", "API_BASE_URL", "\"$url/api/v1\"")
            buildConfigField("String", "MEDIA_BASE_URL", "\"$url\"")
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