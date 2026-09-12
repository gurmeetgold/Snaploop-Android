from pathlib import Path

ROOT = Path('.')


def replace(path: str, old: str, new: str):
    p = ROOT / path
    text = p.read_text()
    if old not in text:
        raise RuntimeError(f'Expected text not found in {path}: {old[:120]!r}')
    p.write_text(text.replace(old, new, 1))


build = ROOT / 'app/build.gradle.kts'
text = build.read_text()

if 'import java.util.Properties' not in text:
    text = 'import java.util.Properties\n\n' + text

old = '''val hasFirebaseConfig = file("google-services.json").exists()
if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
    apply(plugin = "com.google.firebase.firebase-perf")
}
'''
new = '''val snapLoopApplicationId = "com.gurmeetchhiber.snaploop.app"
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
'''
if old not in text:
    raise RuntimeError('Firebase preamble did not match')
text = text.replace(old, new, 1)
text = text.replace('applicationId = "com.snaploop.app"', 'applicationId = snapLoopApplicationId', 1)

old_build_types = '''    buildTypes {
        debug {
            isMinifyEnabled = false
            manifestPlaceholders["snaploopAssociatedDomain"] = "snaploop-dev.web.app"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["snaploopAssociatedDomain"] = "getsnaploop.web.app"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
'''
new_build_types = '''    signingConfigs {
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
'''
if old_build_types not in text:
    raise RuntimeError('Build types block did not match')
text = text.replace(old_build_types, new_build_types, 1)

append = '''

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
        check(firebaseText.contains("\\\"package_name\\\": \\\"$snapLoopApplicationId\\\"")) {
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
'''
if 'verifySnapLoopReleaseInputs' not in text:
    text += append

build.write_text(text)

# Never expose internal model asset paths to end users.
coord = ROOT / 'app/src/main/java/com/snaploop/app/ui/AppCoordinator.kt'
text = coord.read_text()
old_user_message = '''    private fun userMessage(t: Throwable): String {
        val text = t.message?.trim().orEmpty()
        return text.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
    }
'''
new_user_message = '''    private fun userMessage(t: Throwable): String {
        val text = t.message?.trim().orEmpty()
        if (text.contains("glintr100.onnx", ignoreCase = true) ||
            text.contains("models/", ignoreCase = true)
        ) {
            return "SnapLoop couldn't start Face Setup. Please update the app or try again."
        }
        return text.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
    }
'''
if old_user_message not in text:
    raise RuntimeError('AppCoordinator userMessage block did not match')
coord.write_text(text.replace(old_user_message, new_user_message, 1))

# Source-regression test for release-critical invariants.
test = ROOT / 'app/src/test/java/com/snaploop/app/ui/PlayInternalReleaseReadinessTest.kt'
test.write_text('''package com.snaploop.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayInternalReleaseReadinessTest {
    private fun source(path: String): String {
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("$path not found")
    }

    @Test fun `production application id matches Play and Firebase registration`() {
        val gradle = source("build.gradle.kts")
        assertTrue(gradle.contains("com.gurmeetchhiber.snaploop.app"))
        assertTrue(gradle.contains("applicationId = snapLoopApplicationId"))
        assertFalse(gradle.contains("applicationId = \\\"com.snaploop.app\\\""))
    }

    @Test fun `release build hard fails when model firebase or upload signing is missing`() {
        val gradle = source("build.gradle.kts")
        assertTrue(gradle.contains("verifySnapLoopReleaseInputs"))
        assertTrue(gradle.contains("glintr100.onnx"))
        assertTrue(gradle.contains("google-services.json does not contain Android package"))
        assertTrue(gradle.contains("Missing release upload-key configuration"))
        assertTrue(gradle.contains("dependsOn(verifySnapLoopReleaseInputs)"))
    }

    @Test fun `raw face model paths are not exposed to users`() {
        val coordinator = source("src/main/java/com/snaploop/app/ui/AppCoordinator.kt")
        assertTrue(coordinator.contains("SnapLoop couldn't start Face Setup"))
        assertTrue(coordinator.contains("text.contains(\\\"glintr100.onnx\\\""))
    }
}
''')

print('Play internal release readiness patch applied')
