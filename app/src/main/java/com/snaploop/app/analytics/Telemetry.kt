package com.snaploop.app.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.posthog.PersonProfiles
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import com.snaploop.app.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/** Scalar-only telemetry values. Images, paths, URLs, embeddings and arbitrary objects cannot enter this API. */
sealed interface TelemetryValue {
    data class Text(val value: String) : TelemetryValue
    data class Count(val value: Long) : TelemetryValue
    data class Measure(val value: Double) : TelemetryValue
    data class Flag(val value: Boolean) : TelemetryValue
}

/**
 * Canonical cross-platform product event. Construction is limited to the factories below so production
 * egress is auditable. Never add phone numbers, names, photo identifiers/paths/URLs, invite tokens,
 * exact location, face images, embeddings, or user-generated text.
 */
data class TelemetryEvent private constructor(
    val name: String,
    val properties: Map<String, TelemetryValue> = emptyMap(),
) {
    companion object {
        fun appOpened() = TelemetryEvent("app_opened")
        fun signupCompleted() = TelemetryEvent("signup_completed")
        fun loginSucceeded() = TelemetryEvent("login_succeeded")
        fun logoutCompleted() = TelemetryEvent("logout_completed")

        fun phoneVerificationStarted() = TelemetryEvent("phone_verification_started")
        fun phoneVerificationSucceeded(durationMs: Long) = TelemetryEvent(
            "phone_verification_succeeded",
            mapOf("duration_ms" to TelemetryValue.Count(durationMs.coerceAtLeast(0))),
        )
        fun phoneVerificationFailed(reason: String) = TelemetryEvent(
            "phone_verification_failed",
            mapOf("reason" to TelemetryValue.Text(reason.safeEnum())),
        )

        fun onboardingStarted() = TelemetryEvent("onboarding_started")
        fun onboardingCompleted(durationMs: Long) = TelemetryEvent(
            "onboarding_completed",
            mapOf("duration_ms" to TelemetryValue.Count(durationMs.coerceAtLeast(0))),
        )
        fun onboardingStepViewed(step: String) = TelemetryEvent(
            "onboarding_step_viewed",
            mapOf("step" to TelemetryValue.Text(step.safeEnum())),
        )
        fun permissionResult(kind: String, granted: Boolean) = TelemetryEvent(
            "permission_result",
            mapOf("kind" to TelemetryValue.Text(kind.safeEnum()), "granted" to TelemetryValue.Flag(granted)),
        )

        fun faceConsentViewed() = TelemetryEvent("face_consent_viewed")
        fun faceConsentAccepted(countryClass: String) = TelemetryEvent(
            "face_consent_accepted",
            mapOf("jurisdiction_class" to TelemetryValue.Text(countryClass.safeEnum())),
        )
        fun faceConsentDeferred() = TelemetryEvent("face_consent_deferred")
        fun faceConsentWithdrawn() = TelemetryEvent("face_consent_withdrawn")
        fun faceSetupStarted(wasUpdate: Boolean) = TelemetryEvent(
            "face_setup_started",
            mapOf("was_update" to TelemetryValue.Flag(wasUpdate)),
        )
        fun faceSetupPoseCompleted(pose: String, attempt: Int) = TelemetryEvent(
            "face_setup_pose_completed",
            mapOf(
                "pose" to TelemetryValue.Text(pose.safeEnum()),
                "attempt" to TelemetryValue.Count(attempt.coerceAtLeast(1).toLong()),
            ),
        )
        fun faceSetupCompleted(wasUpdate: Boolean, durationMs: Long, attempts: Int) = TelemetryEvent(
            "face_setup_completed",
            mapOf(
                "was_update" to TelemetryValue.Flag(wasUpdate),
                "duration_ms" to TelemetryValue.Count(durationMs.coerceAtLeast(0)),
                "attempts" to TelemetryValue.Count(attempts.coerceAtLeast(1).toLong()),
            ),
        )
        fun faceSetupFailed(reason: String) = TelemetryEvent(
            "face_setup_failed",
            mapOf("reason" to TelemetryValue.Text(reason.safeEnum())),
        )
        fun faceTestCompleted(matched: Boolean, durationMs: Long) = TelemetryEvent(
            "face_test_completed",
            mapOf("matched" to TelemetryValue.Flag(matched), "duration_ms" to TelemetryValue.Count(durationMs.coerceAtLeast(0))),
        )

        fun eventCreateStarted() = TelemetryEvent("event_create_started")
        fun eventCreated(category: String, durationDaysBucket: String) = TelemetryEvent(
            "event_created",
            mapOf(
                "category" to TelemetryValue.Text(category.safeEnum()),
                "duration_bucket" to TelemetryValue.Text(durationDaysBucket.safeEnum()),
            ),
        )
        fun eventCreateFailed(reason: String) = TelemetryEvent(
            "event_create_failed",
            mapOf("reason" to TelemetryValue.Text(reason.safeEnum())),
        )
        fun eventEditStarted() = TelemetryEvent("event_edit_started")
        fun eventEdited(changedFieldCount: Int) = TelemetryEvent(
            "event_edited",
            mapOf("changed_field_count" to TelemetryValue.Count(changedFieldCount.coerceAtLeast(0).toLong())),
        )
        fun eventEditFailed(reason: String) = TelemetryEvent(
            "event_edit_failed",
            mapOf("reason" to TelemetryValue.Text(reason.safeEnum())),
        )
        fun eventDeleted() = TelemetryEvent("event_deleted")
        fun eventViewed(role: String, participantCountBucket: String) = TelemetryEvent(
            "event_viewed",
            mapOf(
                "role" to TelemetryValue.Text(role.safeEnum()),
                "participant_count_bucket" to TelemetryValue.Text(participantCountBucket.safeEnum()),
            ),
        )

        fun inviteOpened(source: String) = TelemetryEvent(
            "invite_opened",
            mapOf("source" to TelemetryValue.Text(source.safeEnum())),
        )
        fun inviteSent(channel: String) = TelemetryEvent(
            "invite_sent",
            mapOf("channel" to TelemetryValue.Text(channel.safeEnum())),
        )
        fun joinStarted(source: String) = TelemetryEvent(
            "join_started",
            mapOf("source" to TelemetryValue.Text(source.safeEnum())),
        )
        fun eventJoined(source: String, durationMs: Long) = TelemetryEvent(
            "event_joined",
            mapOf(
                "source" to TelemetryValue.Text(source.safeEnum()),
                "duration_ms" to TelemetryValue.Count(durationMs.coerceAtLeast(0)),
            ),
        )
        fun joinFailed(source: String, reason: String) = TelemetryEvent(
            "join_failed",
            mapOf("source" to TelemetryValue.Text(source.safeEnum()), "reason" to TelemetryValue.Text(reason.safeEnum())),
        )

        fun scanStarted(source: String, candidateCount: Int) = TelemetryEvent(
            "scan_started",
            mapOf(
                "source" to TelemetryValue.Text(source.safeEnum()),
                "candidate_count" to TelemetryValue.Count(candidateCount.coerceAtLeast(0).toLong()),
            ),
        )
        fun scanCompleted(
            source: String,
            scanned: Int,
            matchedPhotos: Int,
            remaining: Int,
            durationMs: Long,
            alreadyCaughtUp: Boolean,
        ) = TelemetryEvent(
            "scan_completed",
            mapOf(
                "source" to TelemetryValue.Text(source.safeEnum()),
                "scanned" to TelemetryValue.Count(scanned.coerceAtLeast(0).toLong()),
                "matched_photos" to TelemetryValue.Count(matchedPhotos.coerceAtLeast(0).toLong()),
                "remaining" to TelemetryValue.Count(remaining.coerceAtLeast(0).toLong()),
                "duration_ms" to TelemetryValue.Count(durationMs.coerceAtLeast(0)),
                "already_caught_up" to TelemetryValue.Flag(alreadyCaughtUp),
            ),
        )
        fun scanFailed(source: String, reason: String, scanned: Int) = TelemetryEvent(
            "scan_failed",
            mapOf(
                "source" to TelemetryValue.Text(source.safeEnum()),
                "reason" to TelemetryValue.Text(reason.safeEnum()),
                "scanned" to TelemetryValue.Count(scanned.coerceAtLeast(0).toLong()),
            ),
        )
        fun scanInterrupted(reason: String) = TelemetryEvent(
            "scan_interrupted",
            mapOf("reason" to TelemetryValue.Text(reason.safeEnum())),
        )
        fun scanStagePerformance(
            stage: String,
            durationMs: Long,
            sampleRatePercent: Int,
        ) = TelemetryEvent(
            "scan_stage_performance",
            mapOf(
                "stage" to TelemetryValue.Text(stage.safeEnum()),
                "duration_ms" to TelemetryValue.Count(durationMs.coerceAtLeast(0)),
                "sample_rate_percent" to TelemetryValue.Count(sampleRatePercent.coerceIn(1, 100).toLong()),
            ),
        )

        fun galleryViewed(scope: String, photoCount: Int) = TelemetryEvent(
            "matched_gallery_viewed",
            mapOf(
                "scope" to TelemetryValue.Text(scope.safeEnum()),
                "photo_count" to TelemetryValue.Count(photoCount.coerceAtLeast(0).toLong()),
            ),
        )
        fun galleryRefreshCompleted(scope: String, durationMs: Long, addedCount: Int) = TelemetryEvent(
            "gallery_refresh_completed",
            mapOf(
                "scope" to TelemetryValue.Text(scope.safeEnum()),
                "duration_ms" to TelemetryValue.Count(durationMs.coerceAtLeast(0)),
                "added_count" to TelemetryValue.Count(addedCount.coerceAtLeast(0).toLong()),
            ),
        )
        fun matchedPhotoOpened() = TelemetryEvent("matched_photo_opened")
        fun matchedPhotosSaved(count: Int) = TelemetryEvent(
            "matched_photos_saved",
            mapOf("count" to TelemetryValue.Count(count.coerceAtLeast(0).toLong())),
        )
        fun matchedPhotosShared(count: Int) = TelemetryEvent(
            "matched_photos_shared",
            mapOf("count" to TelemetryValue.Count(count.coerceAtLeast(0).toLong())),
        )
        fun matchedPhotoFavoriteChanged(favorited: Boolean) = TelemetryEvent(
            "matched_photo_favorite_changed",
            mapOf("favorited" to TelemetryValue.Flag(favorited)),
        )
        fun matchedPhotoNotMeResult(succeeded: Boolean) = TelemetryEvent(
            "matched_photo_not_me_result",
            mapOf("succeeded" to TelemetryValue.Flag(succeeded)),
        )
        fun firstPhotoDiscovered() = TelemetryEvent("first_photo_discovered")
        fun crossParticipantPhotoReceived() = TelemetryEvent("cross_participant_photo_received")

        fun participantsViewed(countBucket: String) = TelemetryEvent(
            "participants_viewed",
            mapOf("count_bucket" to TelemetryValue.Text(countBucket.safeEnum())),
        )
        fun participantRemoved() = TelemetryEvent("participant_removed")
        fun contentReported() = TelemetryEvent("content_reported")
        fun userBlocked() = TelemetryEvent("user_blocked")

        fun notificationPermissionResult(granted: Boolean) = TelemetryEvent(
            "notification_permission_result",
            mapOf("granted" to TelemetryValue.Flag(granted)),
        )
        fun notificationOpened(type: String) = TelemetryEvent(
            "notification_opened",
            mapOf("type" to TelemetryValue.Text(type.safeEnum())),
        )

        fun accountDeletionStarted() = TelemetryEvent("account_deletion_started")
        fun accountDeletionCompleted() = TelemetryEvent("account_deletion_completed")
        fun accountDeletionFailed(reason: String) = TelemetryEvent(
            "account_deletion_failed",
            mapOf("reason" to TelemetryValue.Text(reason.safeEnum())),
        )

        fun operationPerformance(operation: String, durationMs: Long, success: Boolean) = TelemetryEvent(
            "operation_performance",
            mapOf(
                "operation" to TelemetryValue.Text(operation.safeEnum()),
                "duration_ms" to TelemetryValue.Count(durationMs.coerceAtLeast(0)),
                "success" to TelemetryValue.Flag(success),
            ),
        )
    }
}

private fun String.safeEnum(): String =
    lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_').take(40).ifBlank { "unknown" }

interface TelemetrySink {
    fun capture(event: TelemetryEvent)
    fun identify(userId: String) {}
    fun reset() {}
    fun setCollectionEnabled(enabled: Boolean) {}
}

internal class CompositeTelemetrySink(private val sinks: List<TelemetrySink>) : TelemetrySink {
    override fun capture(event: TelemetryEvent) = sinks.forEach { it.capture(event) }
    override fun identify(userId: String) = sinks.forEach { it.identify(userId) }
    override fun reset() = sinks.forEach { it.reset() }
    override fun setCollectionEnabled(enabled: Boolean) = sinks.forEach { it.setCollectionEnabled(enabled) }
}

private class FirebaseAnalyticsSink(context: Context) : TelemetrySink {
    private val analytics = FirebaseAnalytics.getInstance(context)

    override fun capture(event: TelemetryEvent) {
        val bundle = Bundle()
        event.properties.forEach { (key, value) ->
            when (value) {
                is TelemetryValue.Text -> bundle.putString(key, value.value)
                is TelemetryValue.Count -> bundle.putLong(key, value.value)
                is TelemetryValue.Measure -> bundle.putDouble(key, value.value)
                is TelemetryValue.Flag -> bundle.putLong(key, if (value.value) 1L else 0L)
            }
        }
        analytics.logEvent(event.name, bundle)
    }

    override fun identify(userId: String) {
        analytics.setUserId(userId.trim().takeIf { it.isNotEmpty() })
    }

    override fun reset() {
        analytics.setUserId(null)
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        analytics.setAnalyticsCollectionEnabled(enabled)
    }
}

private class PostHogTelemetrySink : TelemetrySink {
    override fun capture(event: TelemetryEvent) {
        PostHog.capture(
            event = event.name,
            properties = event.properties.mapValues { (_, value) -> value.asPostHogValue() },
        )
    }

    override fun identify(userId: String) {
        val id = userId.trim()
        if (id.isNotEmpty()) PostHog.identify(distinctId = id)
    }

    override fun reset() {
        PostHog.reset()
        registerPrivacyDefaults()
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        if (enabled) PostHog.optIn() else PostHog.optOut()
    }

    companion object {
        fun setup(context: Context, token: String): PostHogTelemetrySink? {
            val normalizedToken = token.trim()
            if (normalizedToken.isEmpty()) return null

            val config = PostHogAndroidConfig(
                apiKey = normalizedToken,
                host = "https://us.i.posthog.com",
            ).apply {
                captureApplicationLifecycleEvents = false
                captureScreenViews = false
                captureDeepLinks = false
                sessionReplay = false
                sendFeatureFlagEvent = false
                preloadFeatureFlags = false
                setDefaultPersonProperties = false
                personProfiles = PersonProfiles.IDENTIFIED_ONLY
                flushAt = 40
                flushIntervalSeconds = 60
                maxBatchSize = 50
                maxQueueSize = 1000
                debug = false
            }
            PostHogAndroid.setup(context, config)
            registerPrivacyDefaults()
            return PostHogTelemetrySink()
        }

        private fun registerPrivacyDefaults() {
            PostHog.register("\$geoip_disable", true)
        }
    }
}

private fun TelemetryValue.asPostHogValue(): Any = when (this) {
    is TelemetryValue.Text -> value
    is TelemetryValue.Count -> value
    is TelemetryValue.Measure -> value
    is TelemetryValue.Flag -> value
}

/** Process-wide entry point. Capture is no-op until Application initialization completes. */
object SnapLoopTelemetry {
    private val initialized = AtomicBoolean(false)
    @Volatile private var sink: TelemetrySink = object : TelemetrySink {
        override fun capture(event: TelemetryEvent) = Unit
    }

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return

        val sinks = mutableListOf<TelemetrySink>()
        if (BuildConfig.FIREBASE_CONFIG_PRESENT) {
            sinks += FirebaseAnalyticsSink(context.applicationContext)
        }
        PostHogTelemetrySink.setup(context.applicationContext, BuildConfig.POSTHOG_PROJECT_TOKEN)?.let(sinks::add)
        sink = CompositeTelemetrySink(sinks)
        sink.setCollectionEnabled(true)
    }

    fun capture(event: TelemetryEvent) = sink.capture(event)

    /** Stable Firebase Auth UID only. Never pass phone number, name, email or an Event/Photo identifier. */
    fun identify(userId: String) = sink.identify(userId)

    fun reset() = sink.reset()

    fun setCollectionEnabled(enabled: Boolean) = sink.setCollectionEnabled(enabled)

    internal fun installForTest(testSink: TelemetrySink) {
        sink = testSink
    }
}
