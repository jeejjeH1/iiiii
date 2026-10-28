plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "app.goallive.live"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.goallive.live"
        minSdk = 24
        targetSdk = 35
        versionCode = 120
        versionName = "3.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Google sign-in's client, built in so the button is there on the very first screen (the server can still change it)
        buildConfigField("String", "GOOGLE_CLIENT", "\"" + (System.getenv("GOOGLE_CLIENT_ID") ?: "") + "\"")
    }

    // permanent store key: the build machine writes it from the KEYSTORE_B64 / KEYSTORE_PASS secrets
    val storeKey = rootProject.file("release.jks")
    signingConfigs {
        if (storeKey.exists()) create("store") {
            storeFile = storeKey
            storePassword = System.getenv("KEYSTORE_PASS")
            keyAlias = "goallive"
            keyPassword = System.getenv("KEYSTORE_PASS")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // without the store key (no secrets yet): the old sideload key, so it still installs
            signingConfig = signingConfigs.findByName("store") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    // screenshots of every screen with live data, rendered on the build machine (Robolectric + Roborazzi)
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // the screenshot run prints why a capture failed
            all { it.testLogging { showStandardStreams = true } }
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // signing in with a Google account
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation(bom)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.32.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // the story video made on a real (emulated) phone: .github/workflows/story-video.yml
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
