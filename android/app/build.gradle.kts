plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

fun envOrProperty(name: String): String? {
    return providers.gradleProperty(name).orNull ?: System.getenv(name)
}

fun versionCodeFrom(versionName: String): Int {
    val match = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""").matchEntire(versionName) ?: return 1
    val (major, minor, patch) = match.destructured
    return (major.toInt() * 1_000_000) + (minor.toInt() * 1_000) + patch.toInt()
}

val releaseVersionName = envOrProperty("VERSION_NAME") ?: "1.0.0"
val requireReleaseSigning = (envOrProperty("CI_RELEASE") ?: "false").toBoolean()
val signingKeystorePath = envOrProperty("ANDROID_KEYSTORE_PATH")
val signingKeystorePassword = envOrProperty("ANDROID_KEYSTORE_PASSWORD")
val signingKeyAlias = envOrProperty("ANDROID_KEY_ALIAS")
val signingKeyPassword = envOrProperty("ANDROID_KEY_PASSWORD")
val hasReleaseSigning =
    !signingKeystorePath.isNullOrBlank() &&
        !signingKeystorePassword.isNullOrBlank() &&
        !signingKeyAlias.isNullOrBlank() &&
        !signingKeyPassword.isNullOrBlank()

if (requireReleaseSigning && !hasReleaseSigning) {
    error(
        "Release signing is required but Android signing secrets are incomplete. " +
            "Please provide ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, " +
            "ANDROID_KEY_ALIAS, and ANDROID_KEY_PASSWORD."
    )
}

android {
    namespace = "com.clipboardsync.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.clipboardsync.app"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeFrom(releaseVersionName)
        versionName = releaseVersionName
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(signingKeystorePath!!)
                storePassword = signingKeystorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        compose = true
    }
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")

    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
