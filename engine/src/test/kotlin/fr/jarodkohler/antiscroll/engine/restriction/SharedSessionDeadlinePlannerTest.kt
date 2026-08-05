package fr.jarodkohler.antiscroll.engine.restriction

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionPolicy
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionState
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedSessionDeadlinePlannerTest {
    private val planner = SharedSessionDeadlinePlanner()
    private val tikTok = ApplicationPackageName("com.zhiliaoapp.musically")
    private val policy = SharedSessionPolicy(
        maximumDuration = Duration.ofMinutes(10),
        inactivityTimeout = Duration.ofMinutes(2)
    )
    private val origin = Instant.parse("2026-08-05T10:00:00Z")

    @Test
    fun inactiveSessionHasNoDeadline() {
        assertNull(planner.plan(SharedSessionState.Inactive, policy))
    }

    @Test
    fun pausedSessionHasNoDeadline() {
        val state = SharedSessionState.Active(
            startedAt = origin,
            startedAtElapsedRealtime = Duration.ZERO,
            accumulatedForegroundDuration = Duration.ofMinutes(4),
            foregroundApplication = null,
            foregroundSinceElapsedRealtime = null,
            inactiveSinceElapsedRealtime = Duration.ofMinutes(4),
            lastObservedAt = origin.plusSeconds(240),
            lastObservedElapsedRealtime = Duration.ofMinutes(4)
        )

        assertNull(planner.plan(state, policy))
    }

    @Test
    fun deadlineIncludesAccumulatedForegroundDuration() {
        val state = activeState(
            accumulatedForegroundDuration = Duration.ofMinutes(4),
            foregroundSinceElapsedRealtime = Duration.ofMinutes(5),
            lastObservedElapsedRealtime = Duration.ofMinutes(7)
        )

        val deadline = requireNotNull(planner.plan(state, policy))

        assertEquals(tikTok, deadline.packageName)
        assertEquals(Duration.ofMinutes(11), deadline.elapsedRealtime)
    }

    @Test
    fun reachedLimitProducesImmediateDeadlineAtLastObservation() {
        val state = activeState(
            accumulatedForegroundDuration = Duration.ofMinutes(8),
            foregroundSinceElapsedRealtime = Duration.ofMinutes(5),
            lastObservedElapsedRealtime = Duration.ofMinutes(8)
        )

        val deadline = requireNotNull(planner.plan(state, policy))

        assertEquals(Duration.ofMinutes(8), deadline.elapsedRealtime)
    }

    private fun activeState(
        accumulatedForegroundDuration: Duration,
        foregroundSinceElapsedRealtime: Duration,
        lastObservedElapsedRealtime: Duration
    ): SharedSessionState.Active = SharedSessionState.Active(
        startedAt = origin,
        startedAtElapsedRealtime = Duration.ZERO,
        accumulatedForegroundDuration = accumulatedForegroundDuration,
        foregroundApplication = tikTok,
        foregroundSinceElapsedRealtime = foregroundSinceElapsedRealtime,
        inactiveSinceElapsedRealtime = null,
        lastObservedAt = origin.plusMillis(lastObservedElapsedRealtime.toMillis()),
        lastObservedElapsedRealtime = lastObservedElapsedRealtime
    )
}
