package com.snaploop.app.core

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

data class RemoteConfigValues(
    val matchConfidenceThreshold: Double = FaceModelPolicy.EVALUATION_MATCH_THRESHOLD,
    val matchAmbiguityMargin: Double = FaceModelPolicy.EVALUATION_AMBIGUITY_MARGIN,
    val minFaceSizeFraction: Double = 0.045,
    val maxAssetsPerSyncBatch: Int = 100,
    val thumbnailMaxPixelSize: Int = 2560,
    val thumbnailJpegQuality: Double = 0.92,
    val signedUrlTtlHours: Int = 48,
    val defaultEventDurationDays: Int = 15,
    val maxEventDurationDays: Int = 15,
    val eventGracePeriodDays: Int = 15,
    val maxParticipantsPerEvent: Int = 250,
    val aiBestShotEnabled: Boolean = true,
    val aiBlurFilterEnabled: Boolean = true,
    val aiHighlightsEnabled: Boolean = true,
) {
    fun asMatchConfig() = MatchConfig(matchConfidenceThreshold, matchAmbiguityMargin, minFaceSizeFraction)

    companion object Keys {
        const val MATCH_THRESHOLD = "face_v5_match_confidence_threshold"
        const val AMBIGUITY_MARGIN = "face_v5_match_ambiguity_margin"
        const val MIN_FACE = "min_face_size_fraction"
        const val MAX_BATCH = "max_assets_per_sync_batch"
        const val THUMBNAIL_PIXELS = "thumbnail_max_pixel_size"
        const val THUMBNAIL_QUALITY = "thumbnail_jpeg_quality"
        const val SIGNED_URL_TTL = "signed_url_ttl_hours"
        const val DEFAULT_EVENT_DAYS = "default_event_duration_days"
        const val MAX_EVENT_DAYS = "max_event_duration_days"
        const val EVENT_GRACE_DAYS = "event_grace_period_days"
        const val MAX_PARTICIPANTS = "max_participants_per_event"
        const val AI_BEST_SHOT = "ai_best_shot_enabled"
        const val AI_BLUR = "ai_blur_filter_enabled"
        const val AI_HIGHLIGHTS = "ai_highlights_enabled"
    }
}

class SnapLoopRemoteConfig(private val firebase: FirebaseRemoteConfig) {
    suspend fun initialize(): RemoteConfigValues {
        firebase.setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = 3600 }).awaitResult()
        firebase.setDefaultsAsync(mapOf(
            RemoteConfigValues.MATCH_THRESHOLD to FaceModelPolicy.EVALUATION_MATCH_THRESHOLD,
            RemoteConfigValues.AMBIGUITY_MARGIN to FaceModelPolicy.EVALUATION_AMBIGUITY_MARGIN,
            RemoteConfigValues.MIN_FACE to 0.045,
            RemoteConfigValues.MAX_BATCH to 100,
            RemoteConfigValues.THUMBNAIL_PIXELS to 2560,
            RemoteConfigValues.THUMBNAIL_QUALITY to 0.92,
            RemoteConfigValues.SIGNED_URL_TTL to 48,
            RemoteConfigValues.DEFAULT_EVENT_DAYS to 15,
            RemoteConfigValues.MAX_EVENT_DAYS to 15,
            RemoteConfigValues.EVENT_GRACE_DAYS to 15,
            RemoteConfigValues.MAX_PARTICIPANTS to 250,
            RemoteConfigValues.AI_BEST_SHOT to true,
            RemoteConfigValues.AI_BLUR to true,
            RemoteConfigValues.AI_HIGHLIGHTS to true,
        )).awaitResult()
        try { firebase.fetchAndActivate().awaitResult() } catch (_: Throwable) { /* fail closed to vetted defaults/cached values */ }
        return readFailClosed()
    }

    private fun readFailClosed(): RemoteConfigValues {
        val threshold = firebase.getDouble(RemoteConfigValues.MATCH_THRESHOLD).takeIf { it in 0.0..1.0 } ?: FaceModelPolicy.EVALUATION_MATCH_THRESHOLD
        val margin = firebase.getDouble(RemoteConfigValues.AMBIGUITY_MARGIN).takeIf { it in 0.0..1.0 } ?: FaceModelPolicy.EVALUATION_AMBIGUITY_MARGIN
        val minFace = firebase.getDouble(RemoteConfigValues.MIN_FACE).takeIf { it in 0.001..1.0 } ?: 0.045
        return RemoteConfigValues(
            matchConfidenceThreshold = threshold,
            matchAmbiguityMargin = margin,
            minFaceSizeFraction = minFace,
            maxAssetsPerSyncBatch = firebase.getLong(RemoteConfigValues.MAX_BATCH).toInt().coerceIn(1, 1000),
            thumbnailMaxPixelSize = firebase.getLong(RemoteConfigValues.THUMBNAIL_PIXELS).toInt().coerceIn(256, 4096),
            thumbnailJpegQuality = firebase.getDouble(RemoteConfigValues.THUMBNAIL_QUALITY).coerceIn(0.5, 1.0),
            signedUrlTtlHours = firebase.getLong(RemoteConfigValues.SIGNED_URL_TTL).toInt().coerceIn(1, 72),
            defaultEventDurationDays = firebase.getLong(RemoteConfigValues.DEFAULT_EVENT_DAYS).toInt().coerceIn(1, 15),
            maxEventDurationDays = firebase.getLong(RemoteConfigValues.MAX_EVENT_DAYS).toInt().coerceIn(1, 15),
            eventGracePeriodDays = firebase.getLong(RemoteConfigValues.EVENT_GRACE_DAYS).toInt().coerceIn(0, 15),
            maxParticipantsPerEvent = firebase.getLong(RemoteConfigValues.MAX_PARTICIPANTS).toInt().coerceIn(2, 250),
            aiBestShotEnabled = firebase.getBoolean(RemoteConfigValues.AI_BEST_SHOT),
            aiBlurFilterEnabled = firebase.getBoolean(RemoteConfigValues.AI_BLUR),
            aiHighlightsEnabled = firebase.getBoolean(RemoteConfigValues.AI_HIGHLIGHTS),
        )
    }
}
