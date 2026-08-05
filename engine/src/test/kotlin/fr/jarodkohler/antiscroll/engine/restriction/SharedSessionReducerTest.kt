package fr.jarodkohler.antiscroll.engine.restriction

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionEndReason
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionEvent
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionPolicy
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionState
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionTransition
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SharedSessionReducerTest {
    private val reducer = SharedSessionReducer()
    private val policy = SharedSessionPolicy(
        maximumDuration = Duration.ofMinutes(10),
        inactivityTimeout = Duration.ofMinutes(2)
    )
    private val tikTok = ApplicationPackageName("com.zhiliaoapp.musically")
    private val instagram = ApplicationPackageName("com.instagram.android")
    private val origin = Instant.parse("2026-08-05T10:00:00Z")

    @Test
    fun monitoredApplicationStartsSharedSession() {
        val reduction = reducer.reduce(
            state = SharedSessionState.Inactive,
            event = foreground(tikTok, minute = 0),
            policy = policy
        )
        val state = reduction.state as SharedSessionState.Active

        assertEquals(tikTok, state.foregroundApplication)
        assertEquals(Duration.ZERO, state.foregroundDurationAt(Duration.ZERO))
        assertEquals(SharedSessionTransition.Started(tikTok), reduction.transition)
    }

    @Test
    fun switchingMonitoredApplicationKeepsSameSessionAndAccumulatedDuration() {
        val started = reducer.reduce(
            SharedSessionState.Inactive,
            foreground(tikTok, minute = 0),
            policy
        ).state
        val reduction = reducer.reduce(started, foreground(instagram, minute = 4), policy)
        val state = reduction.state as SharedSessionState.Active

        assertEquals(origin, state.startedAt)
        assertEquals(instagram, state.foregroundApplication)
        assertEquals(Duration.ofMinutes(10), state.foregroundDurationAt(Duration.ofMinutes(10)))
        assertEquals(
            SharedSessionTransition.ApplicationChanged(tikTok, instagram),
            reduction.transition
        )
    }

    @Test
    fun duplicateForegroundSignalDoesNotRestartCurrentSegment() {
        val started = reducer.reduce(
            SharedSessionState.Inactive,
            foreground(tikTok, minute = 0),
            policy
        ).state
        val reduction = reducer.reduce(started, foreground(tikTok, minute = 2), policy)
        val state = reduction.state as SharedSessionState.Active

        assertEquals(SharedSessionTransition.DuplicateSignal, reduction.transition)
        assertEquals(Duration.ofMinutes(5), state.foregroundDurationAt(Duration.ofMinutes(5)))
    }

    @Test
    fun briefUnmonitoredInterruptionPausesUsageWithoutResettingSession() {
        val started = reducer.reduce(
            SharedSessionState.Inactive,
            foreground(tikTok, minute = 0),
            policy
        ).state
        val paused = reducer.reduce(started, background(tikTok, minute = 4), policy).state
        val reduction = reducer.reduce(paused, foreground(instagram, minute = 5), policy)
        val state = reduction.state as SharedSessionState.Active

        assertEquals(origin, state.startedAt)
        assertEquals(SharedSessionTransition.Resumed(instagram), reduction.transition)
        assertEquals(Duration.ofMinutes(9), state.foregroundDurationAt(Duration.ofMinutes(10)))
    }

    @Test
    fun inactivityAtConfiguredBoundaryStartsNewSession() {
        val started = reducer.reduce(
            SharedSessionState.Inactive,
            foreground(tikTok, minute = 0),
            policy
        ).state
        val paused = reducer.reduce(started, background(tikTok, minute = 4), policy).state
        val reduction = reducer.reduce(paused, foreground(instagram, minute = 6), policy)
        val state = reduction.state as SharedSessionState.Active

        assertEquals(origin.plusSeconds(360), state.startedAt)
        assertEquals(Duration.ZERO, state.foregroundDurationAt(Duration.ofMinutes(6)))
        assertEquals(
            SharedSessionTransition.RestartedAfterInactivity(instagram),
            reduction.transition
        )
    }

    @Test
    fun staleAndNonCurrentBackgroundSignalsDoNotCorruptSession() {
        val started = reducer.reduce(
            SharedSessionState.Inactive,
            foreground(tikTok, minute = 0),
            policy
        ).state
        val switched = reducer.reduce(started, foreground(instagram, minute = 2), policy).state
        val irrelevant = reducer.reduce(switched, background(tikTok, minute = 3), policy)
        val stale = reducer.reduce(irrelevant.state, foreground(tikTok, minute = 1), policy)
        val state = stale.state as SharedSessionState.Active

        assertEquals(SharedSessionTransition.IgnoredIrrelevantSignal, irrelevant.transition)
        assertEquals(SharedSessionTransition.IgnoredStaleSignal, stale.transition)
        assertEquals(instagram, state.foregroundApplication)
        assertEquals(Duration.ofMinutes(4), state.foregroundDurationAt(Duration.ofMinutes(4)))
    }

    @Test
    fun explicitEndRequestClosesSession() {
        val started = reducer.reduce(
            SharedSessionState.Inactive,
            foreground(tikTok, minute = 0),
            policy
        ).state
        val reduction = reducer.reduce(
            started,
            SharedSessionEvent.EndRequested(
                reason = SharedSessionEndReason.LIMIT_REACHED,
                observedAt = origin.plusSeconds(600),
                elapsedRealtime = Duration.ofMinutes(10)
            ),
            policy
        )

        assertSame(SharedSessionState.Inactive, reduction.state)
        assertEquals(
            SharedSessionTransition.Ended(SharedSessionEndReason.LIMIT_REACHED),
            reduction.transition
        )
    }

    private fun foreground(
        packageName: ApplicationPackageName,
        minute: Long
    ): SharedSessionEvent.ApplicationForegrounded =
        SharedSessionEvent.ApplicationForegrounded(
            packageName = packageName,
            observedAt = origin.plusSeconds(minute * 60),
            elapsedRealtime = Duration.ofMinutes(minute)
        )

    private fun background(
        packageName: ApplicationPackageName,
        minute: Long
    ): SharedSessionEvent.ApplicationBackgrounded =
        SharedSessionEvent.ApplicationBackgrounded(
            packageName = packageName,
            observedAt = origin.plusSeconds(minute * 60),
            elapsedRealtime = Duration.ofMinutes(minute)
        )
}
