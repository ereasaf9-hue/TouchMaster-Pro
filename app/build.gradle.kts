plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.touchmaster.pro"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.touchmaster.pro"
        minSdk = 26
    targetSdk = 36
        versionCode = project.findProperty("appVersionCode")?.toString()?.toInt() ?: 1
        versionName = project.findProperty("appVersionName")?.toString() ?: "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val ks = System.getenv("KEYSTORE_PATH")
            val storePass = System.getenv("KEYSTORE_PASSWORD")
            val alias = System.getenv("KEY_ALIAS")
            val keyPass = System.getenv("KEY_PASSWORD")
            if (listOf(ks, storePass, alias, keyPass).all { !it.isNullOrBlank() }) {
                signingConfig = signingConfigs.create("envRelease") {
                    storeFile = file(ks!!); storePassword = storePass; keyAlias = alias; keyPassword = keyPass
                }
            }
        }
    }

    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
