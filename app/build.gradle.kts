import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// The revision a build came from, so that what runs on a device can be traced
// back to a commit. A trailing "+" means the working tree had uncommitted
// changes. Falls back to "unknown" outside a git checkout.
val gitRevision: String = run {

    fun git(vararg args: String) = runCatching {
        providers.exec { commandLine("git", *args) }
                 .standardOutput.asText.get().trim()
    }.getOrNull()

    val revision = git("rev-parse", "--short", "HEAD") ?: return@run "unknown"
    val dirty    = git("status", "--porcelain")?.isNotBlank() == true

    if (dirty) "$revision+" else revision

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
    // Compiling against 37 while still targeting 36: newer APIs become visible,
    // the runtime behaviour the app opts into does not change.
    compileSdk = 37

    defaultConfig {
        applicationId = "cloud.charging.open.dynamicqrcodes"
        minSdk = 31
        targetSdk = 36
        versionCode = 5
        versionName = "1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GIT_REVISION", "\"$gitRevision\"")
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
        buildConfig = true
    }
}

// No kotlin { compilerOptions { jvmTarget } } block: with the built-in Kotlin
// of AGP 9 the JVM target follows compileOptions.targetCompatibility above.

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