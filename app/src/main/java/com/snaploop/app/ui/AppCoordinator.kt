package com.snaploop.app.ui

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.snaploop.app.BuildConfig
import com.snaploop.app.auth.FirebasePhoneAuthService
import com.snaploop.app.auth.PhoneVerificationStart
import com.snaploop.app.core.DeepLinkParser
import com.snaploop.app.core.Embeddings
import com.snaploop.app.core.FaceModelPolicy
import com.snaploop.app.core.RemoteConfigValues
import com.snaploop.app.core.SnapLoopRemoteConfig
import com.snaploop.app.data.FirebaseBiometricConsentStore
import com.snaploop.app.data.FirebaseEventRepository
import com.snaploop.app.data.FirebaseFaceProfileStore
import com.snaploop.app.data.FirebaseMatchRepository
import com.snaploop.app.data.FirebaseUserDirectory
import com.snaploop.app.data.MemberPhotoPreferencesClient
import com.snaploop.app.domain.FaceProfile
import com.snaploop.app.domain.FaceTemplatePose
import com.snaploop.app.domain.FaceTemplateRecord
import com.snaploop.app.domain.PhotoMatch
import com.snaploop.app.domain.SnapUser
import com.snaploop.app.face.AndroidFacePipeline
import com.snaploop.app.face.FaceReferenceCropper
import com.snaploop.app.model.BiometricConsentRecord
import com.snaploop.app.model.EventCategory
import com.snaploop.app.model.EventMember
import com.snaploop.app.model.EventStatus
import com.snaploop.app.model.SnapEvent
import com.snaploop.app.scanner.CameraSyncCoordinator
import com.snaploop.app.scanner.OwnMatchReplayPolicy
import com.snaploop.app.security.EncryptedFaceReferenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

enum class AppGate { RESTORING, AUTH, ONBOARDING, NAME_SETUP, BIOMETRIC_CONSENT, FACE_SETUP, MAIN }
enum class FaceSetupMode { INITIAL_GATE, RETURN_TO_MAIN }

data class AppUiState(
    val gate: AppGate = AppGate.RESTORING,
    val busy: Boolean = false,
    val restoreCanRetry: Boolean = false,
    val restoreError: String? = null,
    val message: String? = null,
    val user: SnapUser? = null,
    val verificationId: String? = null,
    val events: List<SnapEvent> = emptyList(),
    val selectedEvent: SnapEvent? = null,
    val pendingInvite: SnapEvent? = null,
    val members: List<EventMember> = emptyList(),
    val photos: List<PhotoMatch> = emptyList(),
    val allPhotos: List<PhotoMatch> = emptyList(),
    val includeOwnMatches: Boolean = false,
    val scanProgress: CameraSyncCoordinator.Progress? = null,
    val scanResult: CameraSyncCoordinator.Result? = null,
    val faceCaptures: Int = 0,
    val faceSetupMode: FaceSetupMode = FaceSetupMode.INITIAL_GATE,
    val returnToYouAfterFaceSetup: Boolean = false,
)

class AppCoordinator(application: Application) : AndroidViewModel(application) {
    private val auth = FirebasePhoneAuthService()
    private val users = FirebaseUserDirectory()
    private val consent = FirebaseBiometricConsentStore()
    private val faceProfiles = FirebaseFaceProfileStore()
    private val eventRepository = FirebaseEventRepository()
    private val matches = FirebaseMatchRepository()
    private val memberPreferences = MemberPhotoPreferencesClient()
    private val faceReferences = EncryptedFaceReferenceStore(application)
    private val prefs = application.getSharedPreferences("snaploop.ui", 0)
    private val pendingFaceEmbeddings = mutableListOf<FloatArray>()
    private var pendingFaceReferenceJpeg: ByteArray? = null
    private val secureRandom = SecureRandom()

    private var remoteConfig: RemoteConfigValues = RemoteConfigValues()
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    private val _eventGracePeriodDays = MutableStateFlow(RemoteConfigValues().eventGracePeriodDays)
    val eventGracePeriodDays: StateFlow<Int> = _eventGracePeriodDays.asStateFlow()

    init {
        if (BuildConfig.FIREBASE_CONFIG_PRESENT) {
            viewModelScope.launch {
                remoteConfig = withTimeoutOrNull(5_000L) {
                    runCatching {
                        SnapLoopRemoteConfig(FirebaseRemoteConfig.getInstance()).initialize()
                    }.getOrDefault(RemoteConfigValues())
                } ?: RemoteConfigValues()
                _eventGracePeriodDays.value = remoteConfig.eventGracePeriodDays
            }
            restore()
        }
    }

    fun clearMessage() = update { copy(message = null) }

    fun restore() {
        viewModelScope.launch {
            update {
                copy(
                    gate = AppGate.RESTORING,
                    busy = true,
                    restoreCanRetry = false,
                    restoreError = null,
                    message = null,
                )
            }
            try {
                val uid = auth.currentUserId
                if (uid == null) {
                    pendingFaceEmbeddings.clear()
                    update { AppUiState(gate = AppGate.AUTH) }
                    return@launch
                }

                val restored = withTimeoutOrNull(15_000L) {
                    routeAuthenticated(uid)
                    true
                } ?: false

                if (!restored) {
                    update {
                        copy(
                            gate = AppGate.RESTORING,
                            restoreCanRetry = true,
                            restoreError = "Session restore timed out. Check your internet connection and try again.",
                        )
                    }
                }
            } catch (t: Throwable) {
                update {
                    copy(
                        gate = AppGate.RESTORING,
                        restoreCanRetry = true,
                        restoreError = userMessage(t),
                    )
                }
            } finally {
                update { copy(busy = false) }
            }
        }
    }

    fun startPhoneVerification(activity: Activity, phoneNumber: String) = launchBusy {
        when (val result = auth.startPhoneVerification(activity, phoneNumber.trim())) {
            is PhoneVerificationStart.CodeSent ->
                update { copy(gate = AppGate.AUTH, verificationId = result.verificationId, message = null) }
            is PhoneVerificationStart.AutoVerified -> routeAuthenticated(result.userId)
        }
    }

    fun confirmCode(code: String) = launchBusy {
        val verificationId = state.value.verificationId ?: error("Request a verification code first.")
        require(code.filter(Char::isDigit).length >= 6) { "Enter the 6-digit verification code." }
        routeAuthenticated(auth.confirmVerification(verificationId, code.filter(Char::isDigit).take(6)))
    }

    fun useDifferentPhoneNumber() {
        update { copy(verificationId = null, message = null) }
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
        update { copy(gate = AppGate.FACE_SETUP, faceSetupMode = state.value.faceSetupMode) }
    }

    fun addFaceCapture(jpeg: ByteArray) = launchBusy {
        requireUid()
        require(jpeg.isNotEmpty()) { "Camera capture is empty." }
        require(pendingFaceEmbeddings.size < FaceModelPolicy.TARGET_TEMPLATE_COUNT) {
            "Face Setup already has enough captures."
        }

        val captureIndex = pendingFaceEmbeddings.size
        val embedding = withContext(Dispatchers.Default) {
            AndroidFacePipeline(getApplication()).use { it.embeddingForSelfie(jpeg) }
        }

        // Guided poses are intentionally different views of the same person. Comparing every side
        // pose only to the first frontal embedding can reject valid enrollment angles and reduce
        // cross-device recall. Replacement identity protection remains enforced when the complete
        // profile is saved. Prefer the final straight-on frame for the visible local reference.
        if (captureIndex == 0 || captureIndex == FaceModelPolicy.TARGET_TEMPLATE_COUNT - 1) {
            pendingFaceReferenceJpeg = jpeg.copyOf()
        }
        pendingFaceEmbeddings += embedding
        update { copy(faceCaptures = pendingFaceEmbeddings.size, message = null) }
    }

    fun resetFaceCaptures() {
        pendingFaceEmbeddings.clear()
        pendingFaceReferenceJpeg = null
        update { copy(faceCaptures = 0, message = null) }
    }

    /** Opens Face Setup from the authenticated app and always permits returning to Main. */
    fun openFaceSetupFromMain() {
        pendingFaceEmbeddings.clear()
        pendingFaceReferenceJpeg = null
        update {
            copy(
                gate = AppGate.FACE_SETUP,
                faceSetupMode = FaceSetupMode.RETURN_TO_MAIN,
                faceCaptures = 0,
                message = null,
            )
        }
    }

    /** Kept for existing call sites; now uses safe return-to-main semantics. */
    fun replayFaceSetupForUpdate() = openFaceSetupFromMain()

    fun cancelFaceSetup() {
        pendingFaceEmbeddings.clear()
        pendingFaceReferenceJpeg = null
        if (state.value.faceSetupMode == FaceSetupMode.RETURN_TO_MAIN) {
            update { copy(gate = AppGate.MAIN, faceCaptures = 0, message = null) }
        } else {
            skipFaceSetup()
        }
    }

    fun skipFaceSetup() {
        val uid = auth.currentUserId ?: return
        prefs.edit().putBoolean(faceSetupSkippedKey(uid), true).apply()
        pendingFaceEmbeddings.clear()
        pendingFaceReferenceJpeg = null
        viewModelScope.launch { routeAuthenticated(uid) }
    }

    fun completeFaceSetup() = launchBusy {
        val uid = requireUid()
        val returnToYou = state.value.faceSetupMode == FaceSetupMode.RETURN_TO_MAIN
        require(pendingFaceEmbeddings.size == FaceModelPolicy.TARGET_TEMPLATE_COUNT) {
            "Complete all ${FaceModelPolicy.TARGET_TEMPLATE_COUNT} guided face steps."
        }
        val embeddings = pendingFaceEmbeddings.toList()
        val referenceJpeg = pendingFaceReferenceJpeg
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

        // Save only the aligned face crop locally, and only after the server-authoritative profile
        // succeeds. This mirrors iOS and prevents the You/Update Face thumbnail from retaining the
        // full camera frame, shoulders or background.
        val croppedReference = referenceJpeg?.let { bytes ->
            withContext(Dispatchers.Default) {
                runCatching { FaceReferenceCropper.crop(bytes) }.getOrNull()
            }
        }
        if (croppedReference != null) {
            runCatching { faceReferences.save(uid, EncryptedFaceReferenceStore.Kind.GUIDED, croppedReference) }
        }

        prefs.edit().putBoolean(faceSetupSkippedKey(uid), false).apply()
        pendingFaceEmbeddings.clear()
        pendingFaceReferenceJpeg = null
        users.syncMyProfile(uid, state.value.user?.displayName)
        routeAuthenticated(uid)
        if (returnToYou) {
            update { copy(returnToYouAfterFaceSetup = true) }
        }
    }

    fun consumeFaceSetupReturnDestination() {
        update { copy(returnToYouAfterFaceSetup = false) }
    }

    fun refreshEvents() = launchBusy {
        val uid = requireUid()
        val refreshed = eventRepository.eventsForUser(uid)
        update { copy(events = refreshed) }
        refreshAllPhotosInternal(uid, refreshed)
    }

    fun refreshAllPhotos() = launchBusy {
        val uid = requireUid()
        val refreshedEvents = eventRepository.eventsForUser(uid)
        update { copy(events = refreshedEvents) }
        refreshAllPhotosInternal(uid, refreshedEvents)
    }

    /** iOS-parity Event creation using civil dates, category and optional location. */
    fun createEvent(
        name: String,
        category: EventCategory,
        locationName: String?,
        startsOn: LocalDate,
        endsOn: LocalDate,
        onSuccess: () -> Unit = {},
    ) = launchBusy {
        val uid = requireUid()
        require(faceProfiles.load(uid) != null) {
            "Complete Face Setup before creating an Event so SnapLoop can find your photos."
        }
        val clean = name.trim()
        require(clean.isNotEmpty() && clean.length <= 20) { "Event name must be 1–20 characters." }
        validateEventDates(startsOn, endsOn)

        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val event = SnapEvent(
            id = UUID.randomUUID().toString(),
            joinCode = randomJoinCode(),
            inviteToken = randomInviteToken(),
            creatorUserId = uid,
            name = clean,
            category = category,
            locationName = locationName?.trim()?.takeIf { it.isNotEmpty() },
            startsAt = startsOn.atStartOfDay(zone).toInstant(),
            endsAt = endsOn.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1),
            photoWindowVersion = SnapEvent.CANONICAL_PHOTO_WINDOW_VERSION,
            photoWindowTimeZoneId = zone.id,
            photoWindowStartDayNumber = startsOn.toEpochDay().toInt(),
            photoWindowEndDayNumber = endsOn.toEpochDay().toInt(),
            status = EventStatus.active,
            createdAt = now,
            updatedAt = now,
        )
        eventRepository.createEvent(event)

        // Always verify server persistence before refreshing Home. If Firestore is
        // briefly behind, retain the authoritative locally-created object in the
        // visible list rather than showing an empty Home screen.
        val persisted = runCatching { eventRepository.fetchEvent(event.id) }.getOrDefault(event)
        val serverEvents = eventRepository.eventsForUser(uid)
        val merged = if (serverEvents.any { it.id == persisted.id }) serverEvents else listOf(persisted) + serverEvents
        update { copy(events = merged, selectedEvent = null, pendingInvite = null) }
        refreshAllPhotosInternal(uid, merged)
        onSuccess()
    }

    fun editSelectedEvent(
        name: String,
        category: EventCategory,
        locationName: String?,
        startsOn: LocalDate,
        endsOn: LocalDate,
        onSuccess: () -> Unit = {},
    ) = launchBusy {
        val uid = requireUid()
        val current = state.value.selectedEvent ?: error("Open an Event first.")
        val clean = name.trim()
        require(clean.isNotEmpty() && clean.length <= 20) { "Event name must be 1–20 characters." }

        val plan = EventEditParityPolicy.plan(
            current = current,
            name = clean,
            category = category,
            locationName = locationName,
            startsOn = startsOn,
            endsOn = endsOn,
        )
        require(plan.hasChanges) { "No changes to save." }
        if (plan.datesChanged) {
            validateEventDates(startsOn, endsOn)
        }

        eventRepository.updateEvent(
            event = plan.event,
            includeDates = plan.datesChanged,
            expectedUpdatedAt = current.updatedAt,
        )
        val persisted = eventRepository.fetchEvent(current.id)
        loadEvent(persisted)
        update { copy(events = eventRepository.eventsForUser(uid), selectedEvent = persisted) }
        onSuccess()
    }

    fun resolveJoinInput(raw: String) = launchBusy {
        val intent = DeepLinkParser.parseManual(raw)
            ?: error("That QR code, Event code, or invite link isn't valid.")
        com.snaploop.app.core.InvitationResumeStore.capture(intent)
        resolveInvitationInternal(intent.code, intent.token, autoJoin = false)
    }

    fun resolveInvitation(code: String?, token: String?, autoJoin: Boolean = false) = launchBusy {
        resolveInvitationInternal(code, token, autoJoin)
    }

    fun confirmPendingInvite() = launchBusy {
        val uid = requireUid()
        require(faceProfiles.load(uid) != null) {
            "Complete Face Setup before joining an Event so SnapLoop can find your photos."
        }
        val event = state.value.pendingInvite ?: error("Open an invitation first.")
        if (state.value.events.none { it.id == event.id }) {
            eventRepository.join(event.id)
        }
        val persisted = runCatching { eventRepository.fetchEvent(event.id) }.getOrDefault(event)
        val refreshed = eventRepository.eventsForUser(uid)
        update { copy(events = refreshed, pendingInvite = null) }
        loadEvent(persisted)
        refreshAllPhotosInternal(uid, refreshed)
    }

    fun dismissPendingInvite() {
        update { copy(pendingInvite = null, message = null) }
    }

    /** Compatibility for legacy UI/tests: bare code now resolves through the review flow then joins. */
    fun joinEvent(code: String) = launchBusy {
        val normalized = DeepLinkParser.normalizeCode(code) ?: error("Enter a valid 6-character Event code.")
        val event = eventRepository.resolveJoinCode(normalized)
        val uid = requireUid()
        require(faceProfiles.load(uid) != null) { "Complete Face Setup before joining an Event." }
        if (state.value.events.none { it.id == event.id }) eventRepository.join(event.id)
        val refreshed = eventRepository.eventsForUser(uid)
        update { copy(events = refreshed, pendingInvite = null) }
        loadEvent(event)
        refreshAllPhotosInternal(uid, refreshed)
    }

    fun openEvent(event: SnapEvent) = launchBusy { loadEvent(event) }

    fun closeEvent() {
        update {
            copy(
                selectedEvent = null,
                members = emptyList(),
                photos = emptyList(),
                includeOwnMatches = false,
                scanProgress = null,
                scanResult = null,
            )
        }
    }

    fun setSharing(enabled: Boolean) = launchBusy {
        val uid = requireUid()
        val event = state.value.selectedEvent ?: error("Open an Event first.")
        if (!enabled) {
            runCatching { memberPreferences.setIncludeOwnMatches(event.id, false) }
        }
        eventRepository.setSharing(event.id, uid, enabled)
        loadEvent(eventRepository.fetchEvent(event.id))
    }

    fun setIncludeOwnMatches(enabled: Boolean) = launchBusy {
        val uid = requireUid()
        val event = state.value.selectedEvent ?: error("Open an Event first.")
        val me = state.value.members.firstOrNull { it.userId == uid }
            ?: error("Your Event membership could not be loaded.")
        require(me.sharingEnabled) { "Turn on photo sharing for this Event first." }
        val previous = memberPreferences.load(event.id)
        require(previous.sharingEnabled) { "Turn on photo sharing for this Event first." }
        if (enabled) {
            require(faceProfiles.load(uid) != null) { "Set up your face to see your own photo matches." }
        }

        memberPreferences.setIncludeOwnMatches(event.id, enabled)
        val saved = memberPreferences.load(event.id)

        if (
            OwnMatchReplayPolicy.shouldReplay(
                previousEnabled = previous.includeOwnMatches,
                savedEnabled = saved.includeOwnMatches,
                sharingEnabled = saved.sharingEnabled,
                event = event,
                now = Instant.now(),
                gracePeriodDays = remoteConfig.eventGracePeriodDays,
            )
        ) {
            // The preference save is authoritative. Replaying the bounded Event corpus is an
            // opportunistic UX optimization; a permission/device/scanner failure must not turn a
            // successful preference update into an error or roll the saved preference back.
            runCatching {
                withContext(Dispatchers.IO) {
                    CameraSyncCoordinator(getApplication()).use { coordinator ->
                        coordinator.scan(
                            eventId = event.id,
                            startMillis = event.startsAt.toEpochMilli(),
                            endMillis = event.endsAt.toEpochMilli(),
                            sharingEnabled = saved.sharingEnabled,
                            includeOwnMatches = saved.includeOwnMatches,
                            ownMatchesRevision = saved.revisionToken,
                            config = remoteConfig,
                        )
                    }
                }
            }
        }

        loadEvent(eventRepository.fetchEvent(event.id))
        refreshAllPhotosInternal(uid, state.value.events)
    }

    fun leaveSelectedEvent() = launchBusy {
        val uid = requireUid()
        val event = state.value.selectedEvent ?: error("Open an Event first.")
        val me = state.value.members.firstOrNull { it.userId == uid }
        require(me?.role != EventMember.Role.organizer && event.creatorUserId != uid) {
            "The organizer cannot leave their own Event."
        }
        eventRepository.leave(event.id, uid)
        val refreshed = eventRepository.eventsForUser(uid)
        update {
            copy(
                selectedEvent = null,
                members = emptyList(),
                photos = emptyList(),
                includeOwnMatches = false,
                events = refreshed,
                scanProgress = null,
                scanResult = null,
            )
        }
        refreshAllPhotosInternal(uid, refreshed)
    }

    fun removeMember(userId: String) = launchBusy {
        val event = state.value.selectedEvent ?: error("Open an Event first.")
        require(userId != requireUid()) { "Use Leave Event for your own membership." }
        eventRepository.leave(event.id, userId)
        loadEvent(eventRepository.fetchEvent(event.id))
    }

    fun setMemberRole(userId: String, role: EventMember.Role) = launchBusy {
        val event = state.value.selectedEvent ?: error("Open an Event first.")
        eventRepository.setMemberRole(event.id, userId, role)
        loadEvent(eventRepository.fetchEvent(event.id))
    }

    fun endSelectedEvent() = changeSelectedEventStatus(EventStatus.endedByOrganizer)
    fun reopenSelectedEvent() = changeSelectedEventStatus(EventStatus.active)
    fun moveSelectedEventToDeleted() = changeSelectedEventStatus(EventStatus.deletedByOrganizer)
    fun restoreSelectedEvent() = changeSelectedEventStatus(EventStatus.active)

    fun scanSelectedEvent() = launchBusy {
        val uid = requireUid()
        val event = state.value.selectedEvent ?: error("Open an Event first.")
        val preference = memberPreferences.load(event.id)
        require(preference.sharingEnabled) {
            "You have turned off photo sharing for this Event. Turn it on before scanning."
        }
        update { copy(scanProgress = CameraSyncCoordinator.Progress(0, 0, 0), scanResult = null) }
        try {
            val result = withContext(Dispatchers.IO) {
                CameraSyncCoordinator(getApplication()).use { coordinator ->
                    coordinator.scan(
                        eventId = event.id,
                        startMillis = event.startsAt.toEpochMilli(),
                        endMillis = event.endsAt.toEpochMilli(),
                        sharingEnabled = preference.sharingEnabled,
                        includeOwnMatches = preference.includeOwnMatches,
                        ownMatchesRevision = preference.revisionToken,
                        config = remoteConfig,
                        onProgress = { progress -> update { copy(scanProgress = progress) } },
                    )
                }
            }
            update { copy(scanResult = result, scanProgress = null) }
            loadEvent(eventRepository.fetchEvent(event.id))
            refreshAllPhotosInternal(uid, state.value.events)
        } catch (t: Throwable) {
            update { copy(scanProgress = null) }
            throw t
        }
    }

    fun refreshPhotos() = launchBusy {
        val uid = requireUid()
        val event = state.value.selectedEvent ?: error("Open an Event first.")
        refreshPhotosInternal(event.id, uid)
    }

    fun dismissPhoto(match: PhotoMatch) = launchBusy {
        val uid = requireUid()
        matches.dismissAppearance(match.id, uid)
        val event = state.value.selectedEvent
        if (event != null && event.id == match.eventId) refreshPhotosInternal(event.id, uid)
        refreshAllPhotosInternal(uid, state.value.events)
    }

    fun replayOnboarding() {
        val uid = auth.currentUserId ?: return
        prefs.edit().putBoolean(onboardingKey(uid), false).apply()
        update { copy(gate = AppGate.ONBOARDING) }
    }

    fun signOut() {
        auth.signOut()
        pendingFaceEmbeddings.clear()
        pendingFaceReferenceJpeg = null
        // Face reference is intentionally retained encrypted in no-backup storage,
        // matching iOS. Signing out is not a deletion request.
        update { AppUiState(gate = AppGate.AUTH) }
    }

    fun withdrawBiometrics() = launchBusy {
        val uid = requireUid()
        runCatching { memberPreferences.disableOwnMatchesEverywhere() }
        consent.withdraw(uid)
        runCatching { faceProfiles.delete(uid) }
        faceReferences.delete(uid)
        pendingFaceEmbeddings.clear()
        pendingFaceReferenceJpeg = null
        prefs.edit().putBoolean(faceSetupSkippedKey(uid), false).apply()
        update {
            copy(
                gate = AppGate.BIOMETRIC_CONSENT,
                faceSetupMode = FaceSetupMode.INITIAL_GATE,
                faceCaptures = 0,
                photos = emptyList(),
                allPhotos = emptyList(),
                selectedEvent = null,
            )
        }
    }

    fun deleteAccount() = launchBusy {
        val uid = requireUid()
        users.deleteMyAccount(uid)
        faceReferences.delete(uid)
        prefs.edit().remove(onboardingKey(uid)).remove(faceSetupSkippedKey(uid)).apply()
        auth.signOut()
        pendingFaceEmbeddings.clear()
        pendingFaceReferenceJpeg = null
        update { AppUiState(gate = AppGate.AUTH, message = "Account deleted.") }
    }

    private suspend fun resolveInvitationInternal(code: String?, token: String?, autoJoin: Boolean) {
        val event = when {
            !token.isNullOrBlank() -> eventRepository.resolveInviteToken(token)
            !code.isNullOrBlank() -> eventRepository.resolveJoinCode(
                DeepLinkParser.normalizeCode(code) ?: error("Invalid Event code."),
            )
            else -> error("Invite is missing an Event code or token.")
        }
        val uid = requireUid()
        if (state.value.events.any { it.id == event.id }) {
            update { copy(pendingInvite = null) }
            loadEvent(eventRepository.fetchEvent(event.id))
            return
        }
        if (autoJoin) {
            require(faceProfiles.load(uid) != null) { "Complete Face Setup before joining an Event." }
            eventRepository.join(event.id)
            val refreshed = eventRepository.eventsForUser(uid)
            update { copy(events = refreshed, pendingInvite = null) }
            loadEvent(event)
            refreshAllPhotosInternal(uid, refreshed)
        } else {
            update { copy(pendingInvite = event, selectedEvent = null, message = null) }
        }
    }

    private fun changeSelectedEventStatus(status: EventStatus) = launchBusy {
        val uid = requireUid()
        val event = state.value.selectedEvent ?: error("Open an Event first.")
        eventRepository.setStatus(event.id, status)
        val refreshed = eventRepository.eventsForUser(uid)
        update {
            copy(
                events = refreshed,
                selectedEvent = null,
                members = emptyList(),
                photos = emptyList(),
                includeOwnMatches = false,
            )
        }
        refreshAllPhotosInternal(uid, refreshed)
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

        val profile = runCatching { faceProfiles.load(uid) }.getOrNull()
        val resolvedUser = if (user.hasFaceProfile != (profile != null)) user.copy(hasFaceProfile = profile != null) else user
        update { copy(user = resolvedUser) }

        val skippedFaceSetup = prefs.getBoolean(faceSetupSkippedKey(uid), false)
        if (profile == null && !skippedFaceSetup) {
            val currentConsent = consent.load(uid)
            if (currentConsent?.isActive != true) {
                update { copy(gate = AppGate.BIOMETRIC_CONSENT, faceSetupMode = FaceSetupMode.INITIAL_GATE) }
                return
            }
            update { copy(gate = AppGate.FACE_SETUP, faceSetupMode = FaceSetupMode.INITIAL_GATE, faceCaptures = 0) }
            return
        }

        val loadedEvents = eventRepository.eventsForUser(uid)
        update {
            copy(
                gate = AppGate.MAIN,
                user = resolvedUser,
                events = loadedEvents,
                selectedEvent = null,
                pendingInvite = null,
                faceCaptures = 0,
                faceSetupMode = FaceSetupMode.RETURN_TO_MAIN,
            )
        }
        refreshAllPhotosInternal(uid, loadedEvents)
    }

    private suspend fun loadEvent(event: SnapEvent) {
        val uid = requireUid()
        val loadedMembers = eventRepository.members(event.id)
        val fallbackSharing = loadedMembers.firstOrNull { it.userId == uid }?.sharingEnabled ?: false
        val preference = runCatching { memberPreferences.load(event.id) }.getOrNull()
        val sharingEnabled = preference?.sharingEnabled ?: fallbackSharing
        val includeOwn = sharingEnabled && (preference?.includeOwnMatches ?: false)
        val loadedPhotos = matches.myPhotos(event.id, uid).filter {
            it.ownerUserId != uid || includeOwn
        }
        update {
            copy(
                selectedEvent = event,
                pendingInvite = null,
                members = loadedMembers,
                photos = loadedPhotos,
                includeOwnMatches = includeOwn,
                scanProgress = null,
                scanResult = null,
            )
        }
    }

    private suspend fun refreshPhotosInternal(eventId: String, uid: String) {
        val event = state.value.selectedEvent?.takeIf { it.id == eventId }
        val preference = runCatching { memberPreferences.load(eventId) }.getOrNull()
        val includeOwn = preference?.sharingEnabled == true && preference.includeOwnMatches
        val loaded = matches.myPhotos(eventId, uid).filter { it.ownerUserId != uid || includeOwn }
        update {
            copy(
                photos = if (event != null) loaded else photos,
                includeOwnMatches = if (event != null) includeOwn else includeOwnMatches,
            )
        }
    }

    private suspend fun refreshAllPhotosInternal(uid: String, events: List<SnapEvent>) {
        val activeEvents = events.filter { it.status != EventStatus.deletedByOrganizer }
        val collected = mutableListOf<PhotoMatch>()
        for (event in activeEvents) {
            val eventMatches = runCatching { matches.myPhotos(event.id, uid) }.getOrDefault(emptyList())
            if (eventMatches.isEmpty()) continue
            val preference = runCatching { memberPreferences.load(event.id) }.getOrNull()
            val includeOwn = preference?.sharingEnabled == true && preference.includeOwnMatches
            collected += eventMatches.filter { it.ownerUserId != uid || includeOwn }
        }
        val unique = collected
            .distinctBy { "${it.ownerUserId}|${it.sourceInstallationId ?: "legacy"}|${it.assetLocalId}" }
            .sortedByDescending { it.capturedAtMillis }
        update { copy(allPhotos = unique) }
    }

    private fun validateEventDates(startsOn: LocalDate, endsOn: LocalDate) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val lower = today.minusDays(15)
        val upper = today.plusDays(15)
        require(!endsOn.isBefore(startsOn)) { "End date must be on or after the start date." }
        require(!startsOn.isBefore(lower) && !startsOn.isAfter(upper) &&
            !endsOn.isBefore(lower) && !endsOn.isAfter(upper)
        ) { "Event dates must stay within 15 days before or after today." }
        val maxDays = minOf(15, remoteConfig.maxEventDurationDays.coerceAtLeast(1)).toLong()
        val dayDistance = ChronoUnit.DAYS.between(startsOn, endsOn)
        require(dayDistance <= maxDays) { "An Event can span at most $maxDays calendar days." }
    }

    private fun requireUid(): String = auth.currentUserId ?: error("Authentication is required.")

    private fun onboardingKey(uid: String) = "onboarding.$uid.v1"
    private fun faceSetupSkippedKey(uid: String) = "face_setup.skipped.$uid.v1"

    private fun randomJoinCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(6) {
            repeat(6) { append(alphabet[secureRandom.nextInt(alphabet.length)]) }
        }
    }

    private fun randomInviteToken(): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return buildString(22) {
            repeat(22) { append(alphabet[secureRandom.nextInt(alphabet.length)]) }
        }
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
