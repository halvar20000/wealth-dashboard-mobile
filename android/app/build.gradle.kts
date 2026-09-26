plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/** CHANGELOG.md at the top of the repository: one file, both apps. */
val changelog = rootDir.resolve("../CHANGELOG.md")
val changelogAssets = layout.buildDirectory.dir("generated/changelog").get().asFile
val copyChangelog by tasks.registering(Sync::class) {
    from(changelog)
    into(changelogAssets)
}
tasks.named("preBuild") { dependsOn(copyChangelog) }

/** The first `## x.y.z` in the changelog: the release being worked on. */
fun nextVersion(): String =
    Regex("^## (\\d+\\.\\d+\\.\\d+)", RegexOption.MULTILINE)
        .find(if (changelog.exists()) changelog.readText() else "")?.groupValues?.get(1) ?: "0.0.0"

android {
    namespace = "fr.smarthomeworld.wealth"
    compileSdk = 36

    defaultConfig {
        // The name Play locked in when the listing was created. It can
        // never be changed there, so it is the app that gives way. The
        // `namespace` above is only where the classes live and stays
        // put — renaming it would move every source file for nothing.
        applicationId = "com.herbrig.wealthdashboard"
        minSdk = 26
        targetSdk = 36
        // The tag is the version: v1.2.3 builds versionName 1.2.3 and
        // versionCode 10203. A number nobody types by hand is a number
        // nobody forgets to raise — and Play refuses a bundle whose
        // versionCode it has already seen. Off a tag it stays at the
        // version at the top of CHANGELOG.md with "-dev" behind it, so a
        // debug build says which release it is heading for; it never
        // reaches a store.
        val tagged = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$")
            .matchEntire(System.getenv("GITHUB_REF_NAME") ?: "")?.groupValues
        versionCode = if (tagged != null)
            tagged[1].toInt() * 10000 + tagged[2].toInt() * 100 + tagged[3].toInt() else 1
        versionName = if (tagged != null)
            "${tagged[1]}.${tagged[2]}.${tagged[3]}" else "${nextVersion()}-dev"
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
                // Left to the runtime unless the environment names a
                // type: keytool has written PKCS#12 by default since
                // JDK 9, and Java's compatibility mode reads an older
                // JKS through the same default — so a keystore from
                // any tool works here unchanged.
                System.getenv("KEYSTORE_TYPE")?.takeIf { it.isNotBlank() }
                    ?.let { storeType = it }
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
    buildFeatures { compose = true; buildConfig = true }
    // The changelog both apps read lives at the top of the repository,
    // outside the Android project; it rides in the APK as an asset.
    sourceSets["main"].assets.srcDir(changelogAssets)
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
