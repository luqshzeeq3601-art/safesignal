package com.safesignal.protocol

import kotlin.math.min
import kotlin.math.pow

/**
 * Calculates exponential backoff intervals and max attempt limits for alert delivery.
 */
data class RetryPolicy(
    val initialDelayMs: Long = 1000L,
    val maxDelayMs: Long = 15000L,
    val backoffFactor: Double = 2.0,
    val maxAttempts: Int = 10
) {
    /**
     * Returns the backoff delay in milliseconds before dispatching the given attempt.
     * Attempt 1 is immediate (0 ms). Attempt 2 waits initialDelayMs, and so forth.
     */
    fun delayForAttempt(attempt: Int): Long {
        if (attempt <= 1) return 0L
        val exponent = (attempt - 2).coerceAtLeast(0)
        val calculated = (initialDelayMs * backoffFactor.pow(exponent.toDouble())).toLong()
        return min(calculated, maxDelayMs)
    }

    /**
     * Determines whether another delivery attempt should be scheduled.
     */
    fun shouldRetry(attempt: Int): Boolean {
        return attempt < maxAttempts
    }
}
