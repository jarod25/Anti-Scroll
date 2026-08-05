package fr.jarodkohler.antiscroll.restriction

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionDecision
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfile
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfileIdentifier
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfileSource
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionReasonCode
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionEndReason
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionEvent
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionEventSource
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionPolicy
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionState
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionTransition
import fr.jarodkohler.antiscroll.engine.restriction.RestrictionEngine
import fr.jarodkohler.antiscroll.engine.restriction.SessionLimitRule
import fr.jarodkohler.antiscroll.engine.restriction.SharedSessionReducer
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedSessionRuntimeTest {
    private val tikTok = ApplicationPackageName("com.zhiliaoapp.musically")
    private val instagram = ApplicationPackageName("com.instagram.android")
    private val origin = Instant.parse("2026-08-05T10:00:00Z")
    private val observationProfile = RestrictionProfile(
        identifier = RestrictionProfileIdentifier("observation"),
        version = 1,
        sharedSessionPolicy = null
    )
    private val normalProfile = RestrictionProfile(
        identifier = RestrictionProfileIdentifier("normal"),
        version = 1,
        sharedSessionPolicy = SharedSessionPolicy(
            maximumDuration = Duration.ofMinutes(10),
            inactivityTimeout = Duration.ofMinutes(2)
        )
    )

    @Test
    fun startIsIdempotent() = runTest {
        val runtime = runtime(
            profileSource = FakeRestrictionProfileSource(observationProfile),
            eventSource = FakeSharedSessionEventSource(),
            clock = FakeRuntimeClock(origin, Duration.ZERO)
        )

        assertTrue(runtime.start())
        assertFalse(runtime.start())
        assertTrue(runtime.isStarted)
    }

    @Test
    fun observationProfileDoesNotStartRestrictionSession() = runTest {
        val eventSource = FakeSharedSessionEventSource()
        val runtime = runtime(
            profileSource = FakeRestrictionProfileSource(observationProfile),
            eventSource = eventSource,
            clock = FakeRuntimeClock(origin, Duration.ZERO)
        )

        runtime.start()
        runCurrent()
        eventSource.emit(foreground(tikTok, minute = 0))
        runCurrent()

        assertSame(SharedSessionState.Inactive, runtime.snapshots.value.state)
        assertNull(runtime.snapshots.value.lastTransition)
        assertNull(runtime.snapshots.value.lastDecision)
    }

    @Test
    fun activeProfileStartsSessionAndEvaluatesAllowance() = runTest {
        val profileSource = FakeRestrictionProfileSource(observationProfile)
        val eventSource = FakeSharedSessionEventSource()
        val runtime = runtime(
            profileSource = profileSource,
            eventSource = eventSource,
            clock = FakeRuntimeClock(origin, Duration.ZERO)
        )

        runtime.start()
        runCurrent()
        profileSource.replace(normalProfile)
        runCurrent()
        eventSource.emit(foreground(tikTok, minute = 0))
        runCurrent()

        val snapshot = runtime.snapshots.value
        val state = snapshot.state as SharedSessionState.Active
        assertEquals(normalProfile, snapshot.profile)
        assertEquals(tikTok, state.foregroundApplication)
        assertEquals(SharedSessionTransition.Started(tikTok), snapshot.lastTransition)
        assertTrue(snapshot.lastDecision is RestrictionDecision.Allowed)
    }

    @Test
    fun monitoredApplicationSwitchKeepsSessionAndBlocksAtExactLimit() = runTest {
        val eventSource = FakeSharedSessionEventSource()
        val runtime = runtime(
            profileSource = FakeRestrictionProfileSource(normalProfile),
            eventSource = eventSource,
            clock = FakeRuntimeClock(origin, Duration.ZERO)
        )

        runtime.start()
        runCurrent()
        eventSource.emit(foreground(tikTok, minute = 0))
        eventSource.emit(foreground(instagram, minute = 10))
        runCurrent()

        val snapshot = runtime.snapshots.value
        val state = snapshot.state as SharedSessionState.Active
        val decision = snapshot.lastDecision as RestrictionDecision.Blocked
        assertEquals(origin, state.startedAt)
        assertEquals(instagram, state.foregroundApplication)
        assertEquals(Duration.ofMinutes(10), state.foregroundDurationAt(Duration.ofMinutes(10)))
        assertEquals(
            SharedSessionTransition.ApplicationChanged(tikTok, instagram),
            snapshot.lastTransition
        )
        assertEquals(
            RestrictionReasonCode.SESSION_LIMIT_REACHED,
            decision.primaryResult.reasonCode
        )
    }

    @Test
    fun explicitBackgroundTransitionExcludesPausedTime() = runTest {
        val eventSource = FakeSharedSessionEventSource()
        val runtime = runtime(
            profileSource = FakeRestrictionProfileSource(normalProfile),
            eventSource = eventSource,
            clock = FakeRuntimeClock(origin, Duration.ZERO)
        )

        runtime.start()
        runCurrent()
        eventSource.emit(foreground(tikTok, minute = 0))
        eventSource.emit(background(tikTok, minute = 4))
        eventSource.emit(foreground(instagram, minute = 5))
        eventSource.emit(foreground(instagram, minute = 10))
        runCurrent()

        val snapshot = runtime.snapshots.value
        val state = snapshot.state as SharedSessionState.Active
        assertEquals(Duration.ofMinutes(9), state.foregroundDurationAt(Duration.ofMinutes(10)))
        assertEquals(SharedSessionTransition.DuplicateSignal, snapshot.lastTransition)
        assertTrue(snapshot.lastDecision is RestrictionDecision.Allowed)
    }

    @Test
    fun profileChangeEndsActiveSessionAndClearsDecision() = runTest {
        val profileSource = FakeRestrictionProfileSource(normalProfile)
        val eventSource = FakeSharedSessionEventSource()
        val clock = FakeRuntimeClock(origin.plusSeconds(60), Duration.ofMinutes(1))
        val runtime = runtime(profileSource, eventSource, clock)
        val updatedProfile = normalProfile.copy(version = 2)

        runtime.start()
        runCurrent()
        eventSource.emit(foreground(tikTok, minute = 0))
        runCurrent()
        profileSource.replace(updatedProfile)
        runCurrent()

        val snapshot = runtime.snapshots.value
        assertEquals(updatedProfile, snapshot.profile)
        assertSame(SharedSessionState.Inactive, snapshot.state)
        assertEquals(
            SharedSessionTransition.Ended(SharedSessionEndReason.PROFILE_CHANGED),
            snapshot.lastTransition
        )
        assertNull(snapshot.lastDecision)
    }

    private fun TestScope.runtime(
        profileSource: RestrictionProfileSource,
        eventSource: SharedSessionEventSource,
        clock: SharedSessionRuntimeClock
    ): SharedSessionRuntime = SharedSessionRuntime(
        eventSources = setOf(eventSource),
        profileSource = profileSource,
        reducer = SharedSessionReducer(),
        restrictionEngine = RestrictionEngine(listOf(SessionLimitRule())),
        clock = clock,
        applicationScope = backgroundScope
    )

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

    private class FakeRestrictionProfileSource(initialProfile: RestrictionProfile) : RestrictionProfileSource {
        private val mutableActiveProfile = MutableStateFlow(initialProfile)

        override val activeProfile: StateFlow<RestrictionProfile> = mutableActiveProfile.asStateFlow()

        fun replace(profile: RestrictionProfile) {
            mutableActiveProfile.value = profile
        }
    }

    private class FakeSharedSessionEventSource : SharedSessionEventSource {
        private val mutableEvents = MutableSharedFlow<SharedSessionEvent>(extraBufferCapacity = 16)

        override val events: Flow<SharedSessionEvent> = mutableEvents.asSharedFlow()

        fun emit(event: SharedSessionEvent) {
            check(mutableEvents.tryEmit(event)) { "Test event buffer is full" }
        }
    }

    private data class FakeRuntimeClock(
        var currentInstant: Instant,
        var currentElapsedRealtime: Duration
    ) : SharedSessionRuntimeClock {
        override fun now(): Instant = currentInstant

        override fun elapsedRealtime(): Duration = currentElapsedRealtime
    }
}
