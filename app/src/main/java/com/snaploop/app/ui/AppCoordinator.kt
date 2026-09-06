package com.snaploop.app.ui

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.snaploop.app.BuildConfig
import com.snaploop.app.auth.FirebasePhoneAuthService
import com.snaploop.app.auth.PhoneVerificationStart
import com.snaploop.app.core.Embeddings
import com.snaploop.app.core.FaceModelPolicy
import com.snaploop.app.core.RemoteConfigValues
import com.snaploop.app.core.SnapLoopRemoteConfig
import com.snaploop.app.data.FirebaseBiometricConsentStore
import com.snaploop.app.data.FirebaseEventRepository
import com.snaploop.app.data.FirebaseFaceProfileStore
import com.snaploop.app.data.FirebaseMatchRepository
import com.snaploop.app.data.FirebaseUserDirectory
import com.snaploop.app.domain.FaceProfile
import com.snaploop.app.domain.FaceTemplatePose
import com.snaploop.app.domain.FaceTemplateRecord
import com.snaploop.app.domain.PhotoMatch
import com.snaploop.app.domain.SnapUser
import com.snaploop.app.face.AndroidFacePipeline
import com.snaploop.app.model.BiometricConsentRecord
import com.snaploop.app.model.EventCategory
import com.snaploop.app.model.EventMember
import com.snaploop.app.model.SnapEvent
import com.snaploop.app.scanner.CameraSyncCoordinator
import com.snaploop.app.security.EncryptedFaceReferenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.Locale
import java.util.UUID

enum class AppGate { RESTORING, AUTH, ONBOARDING, NAME_SETUP, BIOMETRIC_CONSENT, FACE_SETUP, MAIN }

data class AppUiState(
    val gate: AppGate = AppGate.RESTORING,
    val busy: Boolean = false,
    val message: String? = null,
    val user: SnapUser? = null,
    val verificationId: String? = null,
    val events: List<SnapEvent> = emptyList(),
    val selectedEvent: SnapEvent? = null,
    val members: List<EventMember> = emptyList(),
    val photos: List<PhotoMatch> = emptyList(),
    val scanProgress: CameraSyncCoordinator.Progress? = null,
    val scanResult: CameraSyncCoordinator.Result? = null,
    val faceCaptures: Int = 0,
)

class AppCoordinator(application: Application) : AndroidViewModel(application) {
    private val auth = FirebasePhoneAuthService()
    private val users = FirebaseUserDirectory()
    private val consent = FirebaseBiometricConsentStore()
    private val faceProfiles = FirebaseFaceProfileStore()
    private val events = FirebaseEventRepository()
    private val matches = FirebaseMatchRepository()
    private val faceReferences = EncryptedFaceReferenceStore(application)
    private val prefs = application.getSharedPreferences("snaploop.ui", 0)
    private val pendingFaceJpegs = mutableListOf<ByteArray>()

    private var remoteConfig: RemoteConfigValues = RemoteConfigValues()
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        if (BuildConfig.FIREBASE_CONFIG_PRESENT) {
            viewModelScope.launch {
                remoteConfig = runCatching {
                    SnapLoopRemoteConfig(FirebaseRemoteConfig.getInstance()).initialize()
                }.getOrDefault(RemoteConfigValues())
                restore()
            }
        }
    }

    fun clearMessage() = update { copy(message = null) }

    fun restore() = launchBusy {
        val uid = auth.currentUserId
        if (uid == null) {
            pendingFaceJpegs.clear()
            update { AppUiState(gate = AppGate.AUTH) }
            return@launchBusy
        }
        routeAuthenticated(uid)
    }

    fun startPhoneVerification(activity: Activity, phoneNumber: String) = launchBusy {
        when (val result = auth.startPhoneVerification(activity, phoneNumber.trim())) {
            is PhoneVerificationStart.CodeSent ->
                update { copy(gate = AppGate.AUTH, verificationId = result.verificationId, message = "Verification code sent.") }
            is PhoneVerificationStart.AutoVerified -> routeAuthenticated(result.userId)
        }
    }

    fun confirmCode(code: String) = launchBusy {
        val verificationId = state.value.verificationId
            ?: error("Request a verification code first.")
        routeAuthenticated(auth.confirmVerification(verificationId, code.trim()))
    }

    fun finishOnboarding() {
        val uid = auth.currentUserId ?: return
        prefs.edit().putBoolean(onboardingKey(uid), true).apply()
        viewModelScope.launch { routeAuthenticated(uid) }
    }

    fun saveDisplayName(name: String) = launchBusy {
        val uid = requireUid()
        val clean = name.trim()
        require(clean.length in 2..60) { "Enter your name." }
        users.syncMyProfile(uid, clean)
        routeAuthenticated(uid)
    }

    fun acceptBiometricConsent(
        countryCode: String,
        subdivisionCode: String,
        age18: Boolean,
        noticeAcknowledged: Boolean,
        ownFaceAttested: Boolean,
    ) = launchBusy {
        val uid = requireUid()
        require(age18 && noticeAcknowledged && ownFaceAttested) {
            "All consent confirmations are required before Face Setup."
        }
        val jurisdiction = com.snaploop.app.model.BiometricJurisdiction(countryCode, subdivisionCode)
        require(jurisdiction.isFaceMatchAvailable) {
            "Face matching is not available in the selected jurisdiction."
        }
        consent.save(
            BiometricConsentRecord(
                userId = uid,
                acceptedAt = Instant.now(),
                jurisdictionCountry = jurisdiction.normalizedCountry,
                jurisdictionSubdivision = jurisdiction.normalizedSubdivision,
                appVersion = BuildConfig.VERSION_NAME,
                locale = Locale.getDefault().toLanguageTag(),
                age18Attested = age18,
                noticeAcknowledged = noticeAcknowledged,
                ownFaceAttested = ownFaceAttested,
            )
        )
        val saved = consent.load(uid)
        require(saved?.isActive == true) { "Consent could not be verified. Please try again." }
        routeAuthenticated(uid)
    }

    fun addFaceCapture(jpeg: ByteArray) = launchBusy {
        val uid = requireUid()
        require(jpeg.isNotEmpty()) { "Camera capture is empty." }
        require(pendingFaceJpegs.size < FaceModelPolicy.TARGET_TEMPLATE_COUNT) { "Face Setup already has enough captures." }

        val embedding = withContext(Dispatchers.Default) {
            AndroidFacePipeline(getApplication()).use { it.embeddingForSelfie(jpeg) }
        }
        if (pendingFaceJpegs.isNotEmpty()) {
            val first = withContext(Dispatchers.Default) {
                AndroidFacePipeline(getApplication()).use { it.embeddingForSelfie(pendingFaceJpegs.first()) }
            }
            val similarity = Embeddings.cosine(first, embedding) ?: 0.0
            require(similarity >= FaceModelPolicy.EVALUATION_MATCH_THRESHOLD) {
                "This capture does not appear to be the same person. Retake it."
            }
        }
        pendingFaceJpegs += jpeg
        if (pendingFaceJpegs.size == 1) {
            faceReferences.save(uid, EncryptedFaceReferenceStore.Kind.GUIDED, jpeg)
        }
        update { copy(faceCaptures = pendingFaceJpegs.size, message = "Face capture ${pendingFaceJpegs.size} of ${FaceModelPolicy.TARGET_TEMPLATE_COUNT} accepted.") }
    }

    fun resetFaceCaptures() {
        pendingFaceJpegs.clear()
        update { copy(faceCaptures = 0, message = null) }
    }

    fun replayFaceSetupForUpdate() {
        pendingFaceJpegs.clear()
        update { copy(gate = AppGate.FACE_SETUP, faceCaptures = 0, message = null) }
    }

    fun completeFaceSetup() = launchBusy {
        val uid = requireUid()
        require(pendingFaceJpegs.size >= 3) { "Capture at least 3 guided selfies." }
        val embeddings = withContext(Dispatchers.Default) {
            AndroidFacePipeline(getApplication()).use { pipeline ->
                pendingFaceJpegs.map { pipeline.embeddingForSelfie(it) }
            }
        }
        val average = FloatArray(FaceModelPolicy.EMBEDDING_DIMENSION)
        embeddings.forEach { vector ->
            for (i in vector.indices) average[i] += vector[i]
        }
        val normalized = Embeddings.normalize(average) ?: error("Face profile could not be normalized.")
        val now = System.currentTimeMillis()
        val poses = listOf(
            FaceTemplatePose.CENTER,
            FaceTemplatePose.SIDE_A,
            FaceTemplatePose.SIDE_B,
            FaceTemplatePose.TILTED,
            FaceTemplatePose.ALTERNATE,
        )
        val templates = embeddings.mapIndexed { index, vector ->
            FaceTemplateRecord(
                id = UUID.randomUUID().toString(),
                embedding = vector,
                pose = poses[index],
                quality = 1.0,
                createdAtMillis = now + index,
            )
        }
        faceProfiles.save(
            FaceProfile(
                userId = uid,
                faceIdentityId = null,
                embedding = normalized,
                templates = templates,
                version = FaceModelPolicy.CURRENT_VERSION,
                updatedAtMillis = now,
            )
        )
        pendingFaceJpegs.clear()
        users.syncMyProfile(uid, state.value.user?.displayName)
        routeAuthenticated(uid)
    }

    fun refreshEvents() = launchBusy {
        val uid = requireUid()
        update { copy(events = events.eventsForUser(uid)) }
    }

    fun createEvent(name: String, days: Int) = launchBusy {
        val uid = requireUid()
        val clean = name.trim()
        require(clean.length in 2..80) { "Enter an event name." }
        val duration = days.coerceIn(1, remoteConfig.maxEventDurationDays)
        val now = Instant.now()
        val event = SnapEvent(
            id = UUID.randomUUID().toString(),
            joinCode = randomJoinCode(),
            inviteToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""),
            creatorUserId = uid,
            name = clean,
            category = EventCategory.other,
            startsAt = now,
            endsAt = now.plusSeconds(duration * 86_400L),
            createdAt = now,
        )
        events.createEvent(event)
        val persisted = runCatching { events.fetchEvent(event.id) }.getOrDefault(event)
        loadEvent(persisted)
        update { copy(events = events.eventsForUser(uid), selectedEvent = persisted) }
    }

    fun joinEvent(code: String) = launchBusy {
        val uid = requireUid()
        val event = events.resolveJoinCode(code.trim().uppercase(Locale.US))
        events.join(event.id)
        loadEvent(event)
        update { copy(events = events.eventsForUser(uid), selectedEvent = event) }
    }

    fun openEvent(event: SnapEvent) = launchBusy { loadEvent(event) }

    fun closeEvent() {
        update { copy(selectedEvent = null, members = emptyList(), photos = emptyList(), scanProgress = null, scanResult = null) }
    }

    fun setSharing(enabled: Boolean) = launchBusy {
        val uid = requireUid()
        val event = state.value.selectedEvent ?: error("Open an event first.")
        events.setSharing(event.id, uid, enabled)
        loadEvent(event)
    }

    fun leaveSelectedEvent() = launchBusy {
        val uid = requireUid()
        val event = state.value.selectedEvent ?: error("Open an event first.")
        events.leave(event.id, uid)
        update {
            copy(
                selectedEvent = null,
                members = emptyList(),
                photos = emptyList(),
                events = events.eventsForUser(uid),
                scanProgress = null,
                scanResult = null,
            )
        }
    }

    fun scanSelectedEvent() = launchBusy {
        val uid = requireUid()
        val event = state.value.selectedEvent ?: error("Open an event first.")
        val member = state.value.members.firstOrNull { it.userId == uid }
            ?: error("Your event membership could not be loaded.")
        val result = withContext(Dispatchers.IO) {
            CameraSyncCoordinator(getApplication()).use { coordinator ->
                coordinator.scan(
                    eventId = event.id,
                    startMillis = event.startsAt.toEpochMilli(),
                    endMillis = event.endsAt.toEpochMilli(),
                    sharingEnabled = member.sharingEnabled,
                    config = remoteConfig,
                    onProgress = { progress -> update { copy(scanProgress = progress) } },
                )
            }
        }
        update { copy(scanResult = result, scanProgress = null) }
        refreshPhotosInternal(event.id, uid)
    }

    fun refreshPhotos() = launchBusy {
        val uid = requireUid()
        val event = state.value.selectedEvent ?: error("Open an event first.")
        refreshPhotosInternal(event.id, uid)
    }

    fun dismissPhoto(match: PhotoMatch) = launchBusy {
        val uid = requireUid()
        matches.dismissAppearance(match.id, uid)
        val event = state.value.selectedEvent ?: error("Open an event first.")
        refreshPhotosInternal(event.id, uid)
    }

    fun replayOnboarding() {
        val uid = auth.currentUserId ?: return
        prefs.edit().putBoolean(onboardingKey(uid), false).apply()
        update { copy(gate = AppGate.ONBOARDING) }
    }

    fun signOut() {
        val uid = auth.currentUserId
        auth.signOut()
        pendingFaceJpegs.clear()
        if (uid != null) faceReferences.delete(uid)
        update { AppUiState(gate = AppGate.AUTH) }
    }

    fun withdrawBiometrics() = launchBusy {
        val uid = requireUid()
        consent.withdraw(uid)
        runCatching { faceProfiles.delete(uid) }
        faceReferences.delete(uid)
        pendingFaceJpegs.clear()
        update { copy(gate = AppGate.BIOMETRIC_CONSENT, faceCaptures = 0, photos = emptyList(), selectedEvent = null) }
    }

    fun deleteAccount() = launchBusy {
        val uid = requireUid()
        users.deleteMyAccount(uid)
        faceReferences.delete(uid)
        prefs.edit().remove(onboardingKey(uid)).apply()
        auth.signOut()
        pendingFaceJpegs.clear()
        update { AppUiState(gate = AppGate.AUTH, message = "Account deleted.") }
    }

    private suspend fun routeAuthenticated(uid: String) {
        val user = runCatching { users.fetch(uid) }.getOrElse {
            users.syncMyProfile(uid, null)
            users.fetch(uid)
        }
        update { copy(user = user, verificationId = null) }

        if (!prefs.getBoolean(onboardingKey(uid), false)) {
            update { copy(gate = AppGate.ONBOARDING) }
            return
        }
        if (user.displayName.isNullOrBlank()) {
            update { copy(gate = AppGate.NAME_SETUP) }
            return
        }
        val currentConsent = consent.load(uid)
        if (currentConsent?.isActive != true) {
            update { copy(gate = AppGate.BIOMETRIC_CONSENT) }
            return
        }
        if (faceProfiles.load(uid) == null) {
            update { copy(gate = AppGate.FACE_SETUP) }
            return
        }
        update {
            copy(
                gate = AppGate.MAIN,
                user = user,
                events = events.eventsForUser(uid),
                faceCaptures = 0,
            )
        }
    }

    private suspend fun loadEvent(event: SnapEvent) {
        val uid = requireUid()
        val loadedMembers = events.members(event.id)
        update {
            copy(
                selectedEvent = event,
                members = loadedMembers,
                photos = matches.myPhotos(event.id, uid),
                scanProgress = null,
                scanResult = null,
            )
        }
    }

    private suspend fun refreshPhotosInternal(eventId: String, uid: String) {
        update { copy(photos = matches.myPhotos(eventId, uid)) }
    }

    private fun requireUid(): String = auth.currentUserId ?: error("Authentication is required.")

    private fun onboardingKey(uid: String) = "onboarding.$uid.v1"

    private fun randomJoinCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(6) { repeat(6) { append(alphabet.random()) } }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            update { copy(busy = true, message = null) }
            try {
                block()
            } catch (t: Throwable) {
                update { copy(message = userMessage(t)) }
            } finally {
                update { copy(busy = false) }
            }
        }
    }

    private fun userMessage(t: Throwable): String {
        val text = t.message?.trim().orEmpty()
        return text.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
    }

    private inline fun update(transform: AppUiState.() -> AppUiState) {
        _state.value = _state.value.transform()
    }
}
