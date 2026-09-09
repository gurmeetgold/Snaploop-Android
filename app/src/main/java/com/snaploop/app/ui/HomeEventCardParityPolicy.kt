package com.snaploop.app.ui

import com.snaploop.app.model.EventStatus
import com.snaploop.app.model.SnapEvent
import java.time.Instant

/**
 * Pinned iOS HomeView uses a different grace label from EventDashboardView:
 * Home says WRAPPING UP while the dashboard says PHOTO WINDOW. Keep that
 * deliberate wording difference testable while sharing the same lifecycle rules.
 */
internal object HomeEventCardParityPolicy {
    enum class Tint { BLUE, MINT, AMBER, SECONDARY }

    data class Presentation(
        val text: String,
        val tint: Tint,
    )

    fun presentation(
        event: SnapEvent,
        now: Instant,
        gracePeriodDays: Int = EventDashboardParityPolicy.defaultGracePeriodDays,
    ): Presentation = when (event.status) {
        EventStatus.endedByOrganizer -> Presentation("ENDED", Tint.SECONDARY)
        EventStatus.deletedByOrganizer -> Presentation("DELETED", Tint.SECONDARY)
        EventStatus.expired -> Presentation("COMPLETED", Tint.SECONDARY)
        EventStatus.active -> when (EventDashboardParityPolicy.lifecycle(event, now, gracePeriodDays)) {
            EventDashboardParityPolicy.Lifecycle.UPCOMING -> Presentation("UPCOMING", Tint.BLUE)
            EventDashboardParityPolicy.Lifecycle.ACTIVE -> Presentation("LIVE", Tint.MINT)
            EventDashboardParityPolicy.Lifecycle.GRACE -> Presentation("WRAPPING UP", Tint.AMBER)
            EventDashboardParityPolicy.Lifecycle.EXPIRED -> Presentation("COMPLETED", Tint.SECONDARY)
        }
    }
}
