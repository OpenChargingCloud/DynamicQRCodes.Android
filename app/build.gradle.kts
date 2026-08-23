import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

// Signing credentials live outside the repository. Copy
// keystore.properties.example to keystore.properties and fill it in to have
// release builds signed with the Google Play upload key. Without that file the
// build still works, it just produces an unsigned release.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties     = Properties().apply {
    if (keystorePropertiesFile.exists())
        keystorePropertiesFile.inputStream().use { load(it) }
}

android {
    namespace = "cloud.charging.open.dynamicqrcodes"
    compileSdk = 36

    defaultConfig {
        applicationId = "cloud.charging.open.dynamicqrcodes"
        minSdk = 31
        targetSdk = 36
        versionCode = 4
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile     = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias      = keystoreProperties.getProperty("keyAlias")
                keyPassword   = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (keystorePropertiesFile.exists())
                signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
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
    buildFeatures {
        viewBinding = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity)

    // https://github.com/journeyapps/zxing-android-embedded
    implementation(libs.zxing.android.embedded)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

}