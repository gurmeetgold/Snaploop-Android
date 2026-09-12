import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val snapLoopApplicationId = "com.gurmeetchhiber.snaploop.app"
val firebaseConfigFile = file("google-services.json")
val hasFirebaseConfig = firebaseConfigFile.exists()
val faceModelFile = file("src/main/assets/models/glintr100.onnx")
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}
val hasReleaseSigningConfig = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !keystoreProperties.getProperty(it).isNullOrBlank() }

if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
    apply(plugin = "com.google.firebase.firebase-perf")
}

android {
    namespace = "com.snaploop.app"
    compileSdk = 37

    defaultConfig {
        applicationId = snapLoopApplicationId
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("boolean", "FIREBASE_CONFIG_PRESENT", hasFirebaseConfig.toString())
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            manifestPlaceholders["snaploopAssociatedDomain"] = "snaploop-dev.web.app"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["snaploopAssociatedDomain"] = "getsnaploop.web.app"
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging.resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
}

kotlin { jvmToolchain(17) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    androidTestImplementation(composeBom)
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.navigation:navigation-compose:2.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // In-app camera preview + local analysis for Face Setup and QR joining.
    val cameraXVersion = "1.6.2"
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")

    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-perf")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")

    // Bundled ML models are available immediately and never depend on a first-run download.
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.25.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}


val verifySnapLoopReleaseInputs by tasks.registering {
    group = "verification"
    description = "Fails release builds that would produce a broken or unpublishable SnapLoop bundle."
    doLast {
        check(faceModelFile.isFile && faceModelFile.length() > 0L) {
            "Missing AuraFace model: app/src/main/assets/models/glintr100.onnx"
        }
        check(firebaseConfigFile.isFile) {
            "Missing app/google-services.json for $snapLoopApplicationId"
        }
        val firebaseText = firebaseConfigFile.readText()
        check(firebaseText.contains("\"package_name\": \"$snapLoopApplicationId\"")) {
            "google-services.json does not contain Android package $snapLoopApplicationId"
        }
        check(hasReleaseSigningConfig) {
            "Missing release upload-key configuration in keystore.properties"
        }
        val configuredStore = rootProject.file(keystoreProperties.getProperty("storeFile"))
        check(configuredStore.isFile) {
            "Upload keystore does not exist: ${configuredStore.path}"
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifySnapLoopReleaseInputs)
}
