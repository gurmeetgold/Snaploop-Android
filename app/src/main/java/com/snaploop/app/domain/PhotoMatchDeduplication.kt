package com.snaploop.app.domain

/**
 * Cross-platform photo identity policy mirrored from pinned iOS PhotoMatchDeduplication.
 *
 * During the source-scoped identity migration, one legacy row can temporarily coexist with its
 * modern replacement. For the exact same owner/asset/capture tuple, prefer the modern row. Modern
 * rows from different installations remain distinct because they can represent genuinely different
 * source photos belonging to the same account.
 */
object PhotoMatchDeduplication {
    private fun migrationEquivalenceKey(match: PhotoMatch): String =
        "${match.ownerUserId}|${match.assetLocalId}|${match.capturedAtMillis}"

    fun logicalSourceKey(match: PhotoMatch): String {
        val sourceScope = match.sourceInstallationId?.trim()?.takeIf(String::isNotEmpty) ?: "legacy"
        return "${match.ownerUserId}|$sourceScope|${match.assetLocalId}"
    }

    fun unique(matches: List<PhotoMatch>): List<PhotoMatch> {
        val modernEquivalents = matches
            .asSequence()
            .filter { !it.sourceInstallationId.isNullOrBlank() }
            .map(::migrationEquivalenceKey)
            .toSet()

        val seen = mutableSetOf<String>()
        return matches.filter { match ->
            if (
                match.sourceInstallationId.isNullOrBlank() &&
                migrationEquivalenceKey(match) in modernEquivalents
            ) {
                false
            } else {
                seen.add(logicalSourceKey(match))
            }
        }
    }

    fun isSameLogicalSourcePhoto(lhs: PhotoMatch, rhs: PhotoMatch): Boolean =
        logicalSourceKey(lhs) == logicalSourceKey(rhs)
}
