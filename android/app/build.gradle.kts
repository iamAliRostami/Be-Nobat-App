plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val productionApiUrl = providers.environmentVariable("BENOBAT_API_BASE_URL").orNull?.trimEnd('/')
require(productionApiUrl == null || productionApiUrl.startsWith("https://")) {
    "BENOBAT_API_BASE_URL must use HTTPS."
}
android {
    namespace = "com.leon.be_nobat"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.leon.be_nobat"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/\"")
    }
    val signingPath = providers.environmentVariable("BENOBAT_KEYSTORE_PATH").orNull
    if (signingPath != null) {
        signingConfigs.create("release") {
            storeFile = file(signingPath)
            storePassword = providers.environmentVariable("BENOBAT_KEYSTORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("BENOBAT_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("BENOBAT_KEY_PASSWORD").get()
        }
    }
    buildTypes {
        debug { isDebuggable = true }
        release {
            buildConfigField("String", "API_BASE_URL", "\"${productionApiUrl ?: ""}\"")
            isDebuggable = false
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingPath != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { viewBinding = true; buildConfig = true }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
