package com.snaploop.app.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.snaploop.app.data.FirebaseFaceProfileStore
import com.snaploop.app.security.EncryptedFaceReferenceStore
import kotlinx.coroutines.launch

/**
 * iOS-parity Face Setup deletion.
 *
 * Face Setup deletion is deliberately narrower than biometric-consent withdrawal:
 * it deletes the server face profile and encrypted local reference, then reroutes
 * the authenticated session. The user's biometric consent record is left intact,
 * so routing returns directly to Face Setup rather than the consent screen.
 */
internal fun AppCoordinator.deleteFaceSetupPreservingConsent() {
    val uid = state.value.user?.id ?: return
    val app = getApplication<Application>()

    viewModelScope.launch {
        try {
            FirebaseFaceProfileStore().delete(uid)
            EncryptedFaceReferenceStore(app).delete(uid)
            resetFaceCaptures()

            // Re-resolve the server-authoritative profile/user state. Because the
            // consent record remains active, restore() routes back to FACE_SETUP.
            restore()
        } catch (_: Throwable) {
            // Keep the current Face Setup visible if deletion fails. A subsequent
            // retry is safe because both profile and local-reference deletion are
            // idempotent.
        }
    }
}
