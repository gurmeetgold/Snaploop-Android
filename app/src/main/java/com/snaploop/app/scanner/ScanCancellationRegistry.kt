package com.snaploop.app.scanner

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException

/**
 * Process-local cancellation gate for Event scans.
 *
 * CameraSyncCoordinator persists recipient/photo checkpoints after each asset,
 * so cancelling a session is safe: the next scan resumes from completed work.
 * Starting a newer scan for the same Event invalidates an older in-flight one.
 */
object ScanCancellationRegistry {
    data class Token internal constructor(val eventId: String, val generation: Long)

    private val generations = ConcurrentHashMap<String, AtomicLong>()

    fun start(eventId: String): Token {
        val generation = generations
            .computeIfAbsent(eventId) { AtomicLong(0L) }
            .incrementAndGet()
        return Token(eventId, generation)
    }

    fun cancel(eventId: String) {
        generations.computeIfAbsent(eventId) { AtomicLong(0L) }.incrementAndGet()
    }

    fun ensureActive(token: Token) {
        val current = generations[token.eventId]?.get() ?: token.generation
        if (current != token.generation) {
            throw CancellationException("Scan stopped.")
        }
    }
}
