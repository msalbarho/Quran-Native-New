import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val keystorePropertiesFile = rootProject.file("public/keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "com.quransunah.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.quransunah.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 25
        versionName = "4.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                val storeFileName = keystoreProperties.getProperty("storeFile")
                if (!storeFileName.isNullOrBlank()) {
                    storeFile = rootProject.file("public/$storeFileName")
                    storePassword = keystoreProperties.getProperty("storePassword")
                    keyAlias = keystoreProperties.getProperty("keyAlias")
                    keyPassword = keystoreProperties.getProperty("keyPassword")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isJniDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
        buildConfig = true
    }
    androidResources {
        localeFilters += listOf("en", "ar")
    }
    bundle {
        density {
            enableSplit = true
        }
        language {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
            excludes += "META-INF/*.kotlin_module"
        }
    }
}

fun resolvePythonExecutable(): String? {
    val candidates = listOf(
        System.getenv("LOCALAPPDATA")?.let { "$it\\Programs\\Python\\Python314\\python.exe" },
        System.getenv("LOCALAPPDATA")?.let { "$it\\Programs\\Python\\Python313\\python.exe" },
        System.getenv("LOCALAPPDATA")?.let { "$it\\Programs\\Python\\Python312\\python.exe" },
        "C:\\Program Files\\Python314\\python.exe",
        "C:\\Program Files\\Python313\\python.exe",
        "C:\\Program Files\\Python312\\python.exe",
        "py",
        "python3",
        "python",
    ).filterNotNull()
    for (cmd in candidates) {
        val args = if (cmd == "py") listOf(cmd, "-3", "--version") else listOf(cmd, "--version")
        val proc = runCatching {
            ProcessBuilder(args)
                .redirectErrorStream(true)
                .start()
        }.getOrNull() ?: continue
        val exited = proc.waitFor()
        val out = proc.inputStream.bufferedReader().readText()
        if (exited == 0 && out.contains("Python")) return if (cmd == "py") "py" else cmd
    }
    return null
}

val pythonExecutable = resolvePythonExecutable()
val pythonCommandPrefix: List<String> = when (pythonExecutable) {
    null -> emptyList()
    "py" -> listOf("py", "-3")
    else -> listOf(pythonExecutable)
}

val convertQcf4Fonts by tasks.registering(Exec::class) {
    group = "assets"
    description = "Convert React QCF4 WOFF2 faces to TTF (never package WOFF2)."
    val dest = rootProject.file("public/fonts/qcf4/QCF4_Hafs_47.ttf")
    onlyIf { pythonCommandPrefix.isNotEmpty() && !dest.isFile }
    workingDir = rootProject.projectDir
    commandLine(pythonCommandPrefix + listOf("tools/convert_qcf4_faces.py"))
}

val optimizePackagedFonts by tasks.registering(Exec::class) {
    group = "assets"
    description = "Compact packaged TTFs without dropping QCF ligatures."
    dependsOn(convertQcf4Fonts)
    onlyIf { pythonCommandPrefix.isNotEmpty() }
    workingDir = rootProject.projectDir
    commandLine(pythonCommandPrefix + listOf("tools/optimize_ttf_fonts.py"))
}

val generatedPublicAssets = layout.buildDirectory.dir("generated/publicAssets")
val syncPublicFontAssets by tasks.registering(Copy::class) {
    dependsOn(optimizePackagedFonts)
    doFirst {
        layout.projectDirectory.file("src/main/assets/fonts/qbsml.ttf").asFile.delete()
        val generatedFonts = generatedPublicAssets.get().asFile.resolve("fonts")
        generatedFonts.resolve("hafsq1.ttf").delete()
        generatedFonts.resolve("hafsq2.ttf").delete()
        generatedFonts.resolve("ksax.ttf").delete()
    }
    from(rootProject.file("public/fonts")) {
        include(
            "hafs.ttf",
            "ksa.ttf",
            "qbsml.ttf",
            "juz.ttf",
            "Amiri-Regular.ttf",
            "Amiri-Bold.ttf",
            "NotoNaskhArabic-Regular.ttf",
            "NotoNaskhArabic-Bold.ttf",
            "qcf4/*.ttf",
        )
        exclude("*.woff", "*.woff2", "hafsq1.ttf", "hafsq2.ttf", "ksax.ttf")
    }
    into(generatedPublicAssets.map { it.asFile.resolve("fonts") })
}

val unifiedDbSource = rootProject.file("public/db/quran_unified.db")
val packagedDbDir = layout.projectDirectory.dir("src/main/assets/databases")

val purgeLegacyAssetDb by tasks.registering {
    group = "assets"
    description = "Remove legacy assets/db copies and SQLite sidecar files so they never ship."
    doLast {
        val legacyDb = layout.projectDirectory.dir("src/main/assets/db").asFile
        if (legacyDb.exists()) {
            legacyDb.deleteRecursively()
        }
        packagedDbDir.asFile.listFiles()
            ?.filter { it.name.endsWith(".db-wal") || it.name.endsWith(".db-shm") }
            ?.forEach { it.delete() }
    }
}

val syncPackagedDatabases by tasks.registering(Copy::class) {
    group = "assets"
    description = "Copy the verified immutable Quran database into assets/databases."
    dependsOn(purgeLegacyAssetDb)
    onlyIf { unifiedDbSource.isFile }
    doFirst {
        listOf("quran_final.db", "quran_tafsir.db", "quran-markers.sqlite")
            .map { packagedDbDir.asFile.resolve(it) }
            .filter { it.exists() }
            .forEach { it.delete() }
    }
    from(unifiedDbSource) {
        rename { "quran_unified.db" }
    }
    into(packagedDbDir)
    doLast {
        packagedDbDir.asFile.listFiles()
            ?.filter { it.name.endsWith(".db-wal") || it.name.endsWith(".db-shm") }
            ?.forEach { it.delete() }
    }
}

android.sourceSets.getByName("main").assets.srcDir(generatedPublicAssets)
// Never package the legacy duplicate tree, SQLite runtime sidecars, or source fonts.
android.sourceSets.getByName("main").assets.exclude(
    "**/db/**/*",
    "**/*.db-wal",
    "**/*.db-shm",
    "**/*.woff",
    "**/*.woff2",
)

tasks.configureEach {
    if (name.contains("merge", ignoreCase = true) && name.contains("Assets")) {
        dependsOn(syncPublicFontAssets)
        dependsOn(syncPackagedDatabases)
        dependsOn(purgeLegacyAssetDb)
    }
    if (name == "preBuild") {
        dependsOn(syncPublicFontAssets)
        dependsOn(syncPackagedDatabases)
        dependsOn(purgeLegacyAssetDb)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.transformer)
    // CarConnection broadcasts only — media Auto uses MediaLibraryService, not CarAppService.
    implementation(libs.androidx.car.app)

    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation("androidx.media:media:1.7.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}
