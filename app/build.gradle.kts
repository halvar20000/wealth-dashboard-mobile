plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "fr.smarthomeworld.wealth"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.smarthomeworld.wealth"
        minSdk = 26
        targetSdk = 35
        // The tag is the version: v1.2.3 builds versionName 1.2.3 and
        // versionCode 10203. A number nobody types by hand is a number
        // nobody forgets to raise — and Play refuses a bundle whose
        // versionCode it has already seen. Off a tag it stays at the
        // development version, which never reaches a store.
        val tagged = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$")
            .matchEntire(System.getenv("GITHUB_REF_NAME") ?: "")?.groupValues
        versionCode = if (tagged != null)
            tagged[1].toInt() * 10000 + tagged[2].toInt() * 100 + tagged[3].toInt() else 1
        versionName = if (tagged != null)
            "${tagged[1]}.${tagged[2]}.${tagged[3]}" else "0.2.0"
    }

    // Signed from the environment, or not at all: a keystore in the
    // repository would be a key everybody has. CI writes the file and
    // sets these four; on a laptop without them the release build is
    // simply unsigned, which is what `assembleDebug` is for anyway.
    signingConfigs {
        val keystore = System.getenv("KEYSTORE_FILE")
        if (!keystore.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystore)
                // The upload key is a PKCS#12 file. Java 17 would
                // default to it anyway; saying so means a .jks dropped
                // in later fails loudly instead of at signing time.
                storeType = "PKCS12"
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
}
