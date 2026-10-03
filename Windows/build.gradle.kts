import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20"
    id("org.jetbrains.compose") version "1.7.0"
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("uk.co.caprica:vlcj:4.8.3")
    // XML-Pull-Parser fuer den geteilten EPG-Lader (XMLTV)
    implementation("net.sf.kxml:kxml2:2.3.0")
}

// Datenlogik (Xtream/M3U) wird 1:1 aus der Android-App geteilt – nur reine Kotlin-Dateien ohne Android-Abhaengigkeit.
sourceSets {
    main {
        kotlin {
            srcDir("../app/src/main/java")
            include(
                "com/poweriptv/desktop/**",
                "com/poweriptv/app/data/DesktopBase64.kt",
                "com/poweriptv/app/data/Models.kt",
                "com/poweriptv/app/data/ContentSource.kt",
                "com/poweriptv/app/data/JsonExt.kt",
                "com/poweriptv/app/data/M3uSource.kt",
                "com/poweriptv/app/data/XtreamSource.kt",
                "com/poweriptv/app/data/EpgRepository.kt",
                "com/poweriptv/app/data/PlatformStores.kt",
                "com/poweriptv/app/data/AgeRatingRepository.kt",
                "com/poweriptv/app/parental/ParentalControl.kt",
                "com/poweriptv/app/ai/AiRecommender.kt",
                "com/poweriptv/app/record/StreamCapture.kt",
                "com/poweriptv/app/record/RecordingModels.kt",
                "com/poweriptv/app/download/DownloadRepository.kt",
                "com/poweriptv/app/update/UpdateChecker.kt",
                "com/poweriptv/app/ui/components/CategoryRules.kt",
                "com/poweriptv/app/ui/components/ContentFilter.kt",
            )
        }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Versionsnummer = GitHub-Build-Nummer (wie bei der Android-App: 1.1.<Build>)
val buildNumber = System.getenv("BUILD_NUMBER") ?: "0"

compose.desktop {
    application {
        mainClass = "com.poweriptv.desktop.MainKt"
        jvmArgs += listOf("-Xmx1024m", "-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8")
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Portiva"
            packageVersion = "1.1.$buildNumber"
            description = "Portiva – PowerIPTV"
            vendor = "Portiva"
            copyright = "Portiva – PowerIPTV"
            // VLC (libvlc.dll + plugins) wird im CI nach build/vlc-resources geladen und mitgeliefert
            appResourcesRootDir.set(layout.buildDirectory.dir("vlc-resources"))
            // Komplette Java-Laufzeit mitliefern (TLS, JNA usw. sicher dabei)
            includeAllModules = true
            windows {
                iconFile.set(project.file("icon/portiva.ico"))
                menuGroup = "Portiva"
                shortcut = true
                menu = true
                dirChooser = true
                perUserInstall = true
                // Feste ID, damit neue Versionen die alte sauber ersetzen
                upgradeUuid = "8f3c2a61-5b7e-4d2a-9a51-3c6e0f7d4b19"
            }
        }
    }
}
