package com.safesignal.protocol

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetryPolicyTest {

    @Test
    fun backoffScheduleIncreasesExponentiallyUpToCap() {
        val policy = RetryPolicy(
            initialDelayMs = 1000L,
            maxDelayMs = 8000L,
            backoffFactor = 2.0,
            maxAttempts = 5
        )

        // Attempt 1: immediate
        assertEquals(0L, policy.delayForAttempt(1))
        // Attempt 2: initialDelay (1000 ms)
        assertEquals(1000L, policy.delayForAttempt(2))
        // Attempt 3: 2000 ms
        assertEquals(2000L, policy.delayForAttempt(3))
        // Attempt 4: 4000 ms
        assertEquals(4000L, policy.delayForAttempt(4))
        // Attempt 5: 8000 ms (capped)
        assertEquals(8000L, policy.delayForAttempt(5))
        // Attempt 6+: still capped at 8000 ms
        assertEquals(8000L, policy.delayForAttempt(6))
    }

    @Test
    fun maxAttemptsRespected() {
        val policy = RetryPolicy(maxAttempts = 3)

        assertTrue(policy.shouldRetry(1))
        assertTrue(policy.shouldRetry(2))
        assertFalse(policy.shouldRetry(3))
        assertFalse(policy.shouldRetry(4))
    }
}
