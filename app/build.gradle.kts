import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing: env vars in CI (secrets, see .github/workflows/release.yml), or a
// gitignored keystore.properties in the project root on the maintainer's PC
// (storeFile, storePassword, keyAlias, keyPassword). Without either, release
// builds come out unsigned.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? = System.getenv(env) ?: keystoreProps.getProperty(prop)
val releaseStoreFile: String? = signingValue("RELEASE_STORE_FILE", "storeFile")

// Firebase (Google Play flavor only): env vars in CI, or the gitignored
// firebase.properties (appId, apiKey, projectId, senderId). Missing values
// leave analytics switched off in the play build too.
val firebaseProps = Properties().apply {
    val f = rootProject.file("firebase.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun firebaseValue(env: String, prop: String): String = (System.getenv(env) ?: firebaseProps.getProperty(prop) ?: "").trim()

android {
    namespace = "com.geotagcamera.geotagginglocationonphoto"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.geotagcamera.geotagginglocationonphoto"
        minSdk = 26
        targetSdk = 36
        // Split APKs get versionCode * 10 + ABI digit (see androidComponents below).
        versionCode = 9
        versionName = "1.4.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // foss: Android's own location services, no Google Play services, no analytics.
    // Shipped on GitHub and F-Droid. play: Fused Location Provider and consent-gated
    // Firebase Analytics, shipped on Google Play. Same applicationId and code otherwise.
    flavorDimensions += "distribution"
    productFlavors {
        create("foss") {
            dimension = "distribution"
            buildConfigField("String", "DONATE_URL", "\"https://www.buymeacoffee.com/konko\"")
        }
        // No donate link on Google Play: its Payments policy forbids sending
        // users to outside payment pages for developer tips.
        create("play") {
            dimension = "distribution"
            buildConfigField("String", "DONATE_URL", "\"\"")
            buildConfigField("String", "FIREBASE_APP_ID", "\"${firebaseValue("FIREBASE_APP_ID", "appId")}\"")
            buildConfigField("String", "FIREBASE_API_KEY", "\"${firebaseValue("FIREBASE_API_KEY", "apiKey")}\"")
            buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${firebaseValue("FIREBASE_PROJECT_ID", "projectId")}\"")
            buildConfigField("String", "FIREBASE_SENDER_ID", "\"${firebaseValue("FIREBASE_SENDER_ID", "senderId")}\"")
        }
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("RELEASE_STORE_PASSWORD", "storePassword")
                keyAlias = signingValue("RELEASE_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("RELEASE_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Signed with the permanent release key when it is configured; otherwise
            // left unsigned (what F-Droid expects: it signs its own builds).
            signingConfig = signingConfigs.findByName("release")
            // AGP otherwise packs META-INF/version-control-info.textproto, which
            // depends on the checkout (F-Droid's clone vs ours) and breaks
            // reproducible builds.
            vcsInfo { include = false }
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

    // Legal documents are read in-app from the same files the repository
    // publishes, so the app and GitHub can never disagree (see copyLegalDocs).
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/legal"))

    // Exported Room schemas feed MigrationTestHelper in androidTest.
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // MapLibre ships ~8-11 MB of native code per CPU type. One APK per ABI keeps
    // downloads small; the universal APK stays for anyone unsure which to pick.
    splits {
        abi {
            // App bundles can't carry ABI splits (Play splits them itself).
            isEnable = gradle.startParameter.taskNames.none { it.contains("bundle", ignoreCase = true) }
            reset()
            // -Pabi=arm64-v8a builds just that one (F-Droid builds one ABI per run).
            val only = (project.findProperty("abi") as String?)?.takeIf { it.isNotBlank() }
            if (only != null) include(only) else include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = only == null
        }
    }

    // F-Droid can't verify the encrypted dependency metadata blob, so leave it out.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val copyLegalDocs by tasks.registering(Copy::class) {
    from(rootProject.files("LICENSE", "PRIVACY.md", "TERMS.md", "THIRD_PARTY_NOTICES.md"))
    from(rootProject.file("LICENSES")) { into("licenses") }
    into(layout.buildDirectory.dir("generated/legal/legal"))
}
tasks.named("preBuild") { dependsOn(copyLegalDocs) }

// Each ABI APK needs its own versionCode so stores and in-place updates can
// tell them apart: universal = base*10, then 1 = armeabi-v7a, 2 = arm64-v8a, 3 = x86_64,
// and 9 = the Google Play bundle.
val abiDigit = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86_64" to 3)
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters.find { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }?.identifier
            val base = output.versionCode.orNull ?: 0
            // Google Play builds (one AAB, Play splits it) end in 9, so a Play
            // update always outranks a GitHub/F-Droid APK of the same version.
            val digit = if (variant.flavorName == "play") 9 else abi?.let { abiDigit[it] } ?: 0
            output.versionCode.set(base * 10 + digit)
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    // Committed schemas make every future Room migration reviewable and testable.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Core / Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.location.altitude)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Vector icons only, no emoji glyphs anywhere in the UI. R8 strips the unused ones.
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.kotlinx.serialization.bom))

    // Manifest theme (android:theme="@style/Theme.Material3...") needs the
    // classic Material Components XML styles, Compose material3 alone doesn't ship them
    implementation(libs.material)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // Night / HDR, only where the phone maker exposes them (CameraX Extensions)
    implementation(libs.androidx.camera.extensions)

    // Location
    "playImplementation"(libs.play.services.location)
    // Google Analytics for Firebase: Play build only, consent-gated (analytics/Analytics.kt).
    "playImplementation"(platform(libs.firebase.bom))
    "playImplementation"(libs.firebase.analytics)

    // Map thumbnail + offline maps: MapLibre renders OpenFreeMap vector tiles
    // on the phone. No API key, no Google services, works offline.
    implementation(libs.maplibre.android)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore (stamp field / org branding preferences)
    implementation(libs.androidx.datastore.preferences)

    // EXIF
    implementation(libs.androidx.exifinterface)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Coil for gallery thumbnails
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Real org.json on the unit-test classpath — the android.jar stub throws
    // "not mocked", so JVM tests of UserCommentCodec need the actual impl.
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
