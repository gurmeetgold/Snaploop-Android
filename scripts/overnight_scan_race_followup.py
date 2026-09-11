from pathlib import Path

ROOT = Path('.')

def read(path):
    return (ROOT / path).read_text()

def write(path, text):
    (ROOT / path).write_text(text)

def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)

# Manual Scan must announce itself before any network preparation. SnapLoopRoot observes
# scanProgress and cancels an in-flight automatic foreground pass before the manual scanner starts.
rel = 'app/src/main/java/com/snaploop/app/ui/AppCoordinator.kt'
s = read(rel)
old = '''    fun scanSelectedEvent() = launchBusy {
        val uid = requireUid()
        val event = state.value.selectedEvent ?: error("Open an Event first.")
        val preference = memberPreferences.load(event.id)
        require(preference.sharingEnabled) {
            "You have turned off photo sharing for this Event. Turn it on before scanning."
        }
        update { copy(scanProgress = CameraSyncCoordinator.Progress(0, 0, 0), scanResult = null) }
        try {
            val result = withContext(Dispatchers.IO) {'''
new = '''    fun scanSelectedEvent() = launchBusy {
        val uid = requireUid()
        val event = state.value.selectedEvent ?: error("Open an Event first.")
        // Publish manual-scan intent before preference/manifest I/O. The root uses this signal to
        // cancel opportunistic automatic scanning for the same foreground session, preventing a
        // later automatic ScanCancellationRegistry.start() from invalidating the user's scan.
        update { copy(scanProgress = CameraSyncCoordinator.Progress(0, 0, 0), scanResult = null) }
        try {
            val preference = memberPreferences.load(event.id)
            require(preference.sharingEnabled) {
                "You have turned off photo sharing for this Event. Turn it on before scanning."
            }
            val result = withContext(Dispatchers.IO) {'''
s = replace_once(s, old, new, 'manual scan announces before I/O')
write(rel, s)

rel = 'app/src/main/java/com/snaploop/app/ui/SnapLoopRoot.kt'
s = read(rel)
old = '''    LaunchedEffect(state.gate, state.events, scanTriggerGeneration) {
        if (
            state.gate == AppGate.MAIN &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            automaticScanner.request(
                events = state.events,
                triggerGeneration = scanTriggerGeneration,
            )
        }
    }'''
new = '''    LaunchedEffect(state.gate, state.events, scanTriggerGeneration, state.scanProgress) {
        if (state.scanProgress != null) {
            // A manual scan owns the foreground scanner slot. Automatic and manual coordinators
            // share ScanCancellationRegistry, so allowing them to overlap can make an automatic
            // generation invalidate a user-initiated pass and surface a false “Scan stopped”.
            automaticScanner.cancelActive()
        } else if (
            state.gate == AppGate.MAIN &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            automaticScanner.request(
                events = state.events,
                triggerGeneration = scanTriggerGeneration,
            )
        }
    }'''
s = replace_once(s, old, new, 'suppress automatic scanner during manual pass')
old = '''                    if (current.gate == AppGate.MAIN) {
                        automaticScanner.request(
                            events = current.events,
                            triggerGeneration = "${current.selectedEvent?.id.orEmpty()}:own=${current.includeOwnMatches}",
                        )
                    }'''
new = '''                    if (current.gate == AppGate.MAIN && current.scanProgress == null) {
                        automaticScanner.request(
                            events = current.events,
                            triggerGeneration = "${current.selectedEvent?.id.orEmpty()}:own=${current.includeOwnMatches}",
                        )
                    } else if (current.scanProgress != null) {
                        automaticScanner.cancelActive()
                    }'''
s = replace_once(s, old, new, 'resume cannot race manual scan')
write(rel, s)

# Serialize only the first direct-Storage capability probe. Without this, a newly opened grid can
# launch several cells concurrently and every cell can wait for the same denied Storage request
# before falling back to the authorized callable.
rel = 'app/src/main/java/com/snaploop/app/ui/MatchedThumbnailLoader.kt'
s = read(rel)
s = replace_once(s, 'import kotlinx.coroutines.tasks.await\n', 'import kotlinx.coroutines.tasks.await\nimport kotlinx.coroutines.sync.Mutex\nimport kotlinx.coroutines.sync.withLock\n', 'thumbnail probe mutex imports')
s = replace_once(
    s,
    '''    private companion object {
        @Volatile var directStorageReadable: Boolean? = null
    }''',
    '''    private companion object {
        @Volatile var directStorageReadable: Boolean? = null
        val directStorageProbeMutex = Mutex()
    }''',
    'thumbnail shared probe mutex',
)
old = '''    private suspend fun fetchBytes(path: String): ByteArray {
        if (directStorageReadable == false) return authorizedFallback(path)
        return try {
            // Published previews are already bounded by SnapLoop's thumbnail policy. Keep this cap
            // larger than iOS's current maximum to tolerate older rows during migration.
            storage.reference.child(path).getBytes(12L * 1024L * 1024L).await().also {
                directStorageReadable = true
            }
        } catch (error: Throwable) {
            // Production recipient rules can intentionally reject direct object reads. Remember
            // that authorization result process-wide so the other Gallery cells go straight to the
            // authorized callable instead of each waiting for an identical failing Storage request.
            if ((error as? StorageException)?.errorCode == StorageException.ERROR_NOT_AUTHORIZED) {
                directStorageReadable = false
            }
            authorizedFallback(path)
        }
    }'''
new = '''    private suspend fun fetchBytes(path: String): ByteArray {
        return when (directStorageReadable) {
            false -> authorizedFallback(path)
            true -> directStorageOrFallback(path)
            null -> directStorageProbeMutex.withLock {
                // Re-check after waiting: another Gallery cell may have completed the probe.
                when (directStorageReadable) {
                    false -> authorizedFallback(path)
                    true -> directStorageOrFallback(path)
                    null -> directStorageOrFallback(path)
                }
            }
        }
    }

    private suspend fun directStorageOrFallback(path: String): ByteArray = try {
        // Published previews are already bounded by SnapLoop's thumbnail policy. Keep this cap
        // larger than iOS's current maximum to tolerate older rows during migration.
        storage.reference.child(path).getBytes(12L * 1024L * 1024L).await().also {
            directStorageReadable = true
        }
    } catch (error: Throwable) {
        // Production recipient rules can intentionally reject direct object reads. Remember
        // that authorization result process-wide so the other Gallery cells go straight to the
        // authorized callable instead of each waiting for an identical failing Storage request.
        if ((error as? StorageException)?.errorCode == StorageException.ERROR_NOT_AUTHORIZED) {
            directStorageReadable = false
        }
        authorizedFallback(path)
    }'''
s = replace_once(s, old, new, 'serialize thumbnail capability probe')
write(rel, s)

# Regression contract for the exact spontaneous-stop race and parallel thumbnail fallback.
test_rel = 'app/src/test/java/com/snaploop/app/ui/ManualScanRaceRegressionTest.kt'
test = r'''package com.snaploop.app.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualScanRaceRegressionTest {
    private fun source(path: String): String {
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("$path not found")
    }

    @Test fun `manual scan publishes preparation state before preference IO`() {
        val source = source("src/main/java/com/snaploop/app/ui/AppCoordinator.kt")
        val method = source.substring(source.indexOf("fun scanSelectedEvent"), source.indexOf("fun refreshPhotos"))
        val progress = method.indexOf("scanProgress = CameraSyncCoordinator.Progress(0, 0, 0)")
        val preference = method.indexOf("memberPreferences.load(event.id)")
        assertTrue(progress >= 0 && preference >= 0 && progress < preference)
    }

    @Test fun `automatic foreground scanner is cancelled while manual progress exists`() {
        val root = source("src/main/java/com/snaploop/app/ui/SnapLoopRoot.kt")
        assertTrue(root.contains("scanTriggerGeneration, state.scanProgress"))
        assertTrue(root.contains("if (state.scanProgress != null)"))
        assertTrue(root.contains("automaticScanner.cancelActive()"))
        assertTrue(root.contains("current.gate == AppGate.MAIN && current.scanProgress == null"))
    }

    @Test fun `gallery serializes first direct storage authorization probe`() {
        val loader = source("src/main/java/com/snaploop/app/ui/MatchedThumbnailLoader.kt")
        assertTrue(loader.contains("directStorageProbeMutex = Mutex()"))
        assertTrue(loader.contains("directStorageProbeMutex.withLock"))
        assertTrue(loader.contains("private suspend fun directStorageOrFallback"))
    }
}
'''
write(test_rel, test)
print('Manual scan race and thumbnail probe follow-up applied')
