plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.webviewexoplayertvapp"  // Replace with your actual package/namespace
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.webviewexoplayertvapp"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation("androidx.core:core:1.13.1")  // Java-friendly core (no -ktx)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    implementation("androidx.leanback:leanback:1.2.0")

    // WebView enhancements (latest stable)
    implementation("androidx.webkit:webkit:1.12.0")

    // Media3 ExoPlayer (latest stable: 1.8.0)
    implementation("androidx.media3:media3-exoplayer:1.8.0")  // Core player
    implementation("androidx.media3:media3-exoplayer-dash:1.8.0")  // For DASH streams
    implementation("androidx.media3:media3-ui:1.8.0")  // For player UI/controls
    implementation("androidx.media3:media3-common:1.8.0")  // Common utils
    implementation("androidx.media3:media3-datasource-okhttp:1.8.0")  // For HTTP with custom headers

    // DRM support (includes Widevine)
//    implementation("androidx.media3:media3-drm:1.8.0")

    // JSON parsing (latest)
    implementation("com.google.code.gson:gson:2.11.0")
}