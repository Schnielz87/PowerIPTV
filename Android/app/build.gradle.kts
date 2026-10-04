plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.poweriptv.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.poweriptv.app"
        minSdk = 24
        targetSdk = 36
        // Build-Nummer von GitHub Actions -> jede Release-APK hat eine hoehere Version
        val build = (System.getenv("BUILD_NUMBER") ?: "1").toInt()
        versionCode = 100 + build
        versionName = "1.1.$build"

        // Nur ARM: Handys, Tablets und alle Fire TV Sticks (haelt die APK trotz VLC klein)
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    // Zwei Varianten aus demselben Code:
    //  github = APK fuer GitHub-Releases (mit eigener Update-Funktion und VPN)
    //  play   = App-Bundle fuer Google Play (Updates nur ueber den Play Store, ohne VPN – Play-Richtlinien)
    flavorDimensions += "store"
    productFlavors {
        create("github") {
            dimension = "store"
            buildConfigField("boolean", "PLAY_STORE", "false")
        }
        create("play") {
            dimension = "store"
            buildConfigField("boolean", "PLAY_STORE", "true")
        }
    }

    signingConfigs {
        // Fester Signatur-Schluessel aus GitHub-Secrets (siehe README) -> Updates ohne Neuinstallation
        val ksPath = System.getenv("SIGNING_KEYSTORE_PATH")
        if (!ksPath.isNullOrBlank() && file(ksPath).exists()) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Eigener Schluessel falls vorhanden, sonst Debug-Key (installierbar, aber Updates nur nach Neuinstallation)
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            // WireGuard liefert native Libraries (libwg-go.so)
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Player
    val media3 = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")

    // Netzwerk / JSON / Bilder
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // QR-Codes: Zugang auf ein anderes Geraet uebertragen (Portiva Link) – Erzeugen + Scannen mit der Kamera
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")

    // VPN (offizielle WireGuard Tunnel-Library)
    implementation("com.wireguard.android:tunnel:1.0.20260102")

    // Google Cast (Chromecast / Google TV)
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")

    // VLC als Kompatibilitaets-Player (MPEG-2, HEVC, Interlaced, ...)
    implementation("org.videolan.android:libvlc-all:3.7.7")
    // libVLC zieht eine alte Fragment-Version mit -> aktuelle erzwingen (ActivityResult-APIs)
    implementation("androidx.fragment:fragment-ktx:1.8.3")
}
