package com.snaploop.app.domain

/**
 * Presentation-time photo deduplication for backend match rows.
 *
 * Source-scoped IDs intentionally distinguish installations so two phones signed into the same
 * account cannot overwrite one another. A reinstall, however, can rotate that installation scope
 * and replay the same local media asset, leaving two backend rows for one physical photo.
 *
 * We never delete either backend row here. For the exact same owner/asset/capture tuple we collapse
 * the rows only for presentation, preferring the most recently matched row (normally the replay
 * produced after a Face Setup refresh/reinstall). Different asset IDs or capture instants remain
 * distinct, even when they come from different installations.
 */
object PhotoMatchDeduplication {
    private fun presentationEquivalenceKey(match: PhotoMatch): String =
        "${match.ownerUserId}|${match.assetLocalId}|${match.capturedAtMillis}"

    fun logicalSourceKey(match: PhotoMatch): String {
        val sourceScope = match.sourceInstallationId?.trim()?.takeIf(String::isNotEmpty) ?: "legacy"
        return "${match.ownerUserId}|$sourceScope|${match.assetLocalId}"
    }

    fun unique(matches: List<PhotoMatch>): List<PhotoMatch> {
        if (matches.size < 2) return matches

        val winnerByKey = linkedMapOf<String, PhotoMatch>()
        matches.forEach { candidate ->
            val key = presentationEquivalenceKey(candidate)
            val current = winnerByKey[key]
            if (current == null || shouldPrefer(candidate, current)) {
                winnerByKey[key] = candidate
            }
        }

        // Preserve the caller's ordering while substituting the selected row for each logical photo.
        // This keeps existing gallery ordering stable and makes the policy safe for all call sites.
        val emitted = mutableSetOf<String>()
        return buildList {
            matches.forEach { original ->
                val key = presentationEquivalenceKey(original)
                if (emitted.add(key)) add(winnerByKey.getValue(key))
            }
        }
    }

    private fun shouldPrefer(candidate: PhotoMatch, current: PhotoMatch): Boolean {
        if (candidate.matchedAtMillis != current.matchedAtMillis) {
            return candidate.matchedAtMillis > current.matchedAtMillis
        }
        val candidateModern = !candidate.sourceInstallationId.isNullOrBlank()
        val currentModern = !current.sourceInstallationId.isNullOrBlank()
        if (candidateModern != currentModern) return candidateModern
        return candidate.id > current.id
    }

    fun isSameLogicalSourcePhoto(lhs: PhotoMatch, rhs: PhotoMatch): Boolean =
        logicalSourceKey(lhs) == logicalSourceKey(rhs)

    fun isSamePresentedPhoto(lhs: PhotoMatch, rhs: PhotoMatch): Boolean =
        presentationEquivalenceKey(lhs) == presentationEquivalenceKey(rhs)
}
