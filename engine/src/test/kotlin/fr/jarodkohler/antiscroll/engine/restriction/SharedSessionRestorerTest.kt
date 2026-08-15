package fr.jarodkohler.antiscroll.engine.restriction

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.restriction.DeviceBootIdentifier
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfile
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfileIdentifier
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionCheckpoint
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionPolicy
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionState
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedSessionRestorerTest {
    private val tikTok = ApplicationPackageName("com.zhiliaoapp.musically")
    private val origin = Instant.parse("2026-08-15T10:00:00Z")
    private val profile = RestrictionProfile(
        identifier = RestrictionProfileIdentifier("normal"),
        version = 1,
        sharedSessionPolicy = SharedSessionPolicy(
            maximumDuration = Duration.ofMinutes(10),
            inactivityTimeout = Duration.ofMinutes(2)
        )
    )

    @Test
    fun sameBootRestoresMonotonicAnchorsExactly() {
        val state = activeState(lastObserved = Duration.ofMinutes(4))
        val checkpoint = SharedSessionCheckpoint.from(profile, state, DeviceBootIdentifier(12))

        val restored = requireNotNull(
            SharedSessionRestorer().restore(
                checkpoint = checkpoint,
                profile = profile,
                currentBootIdentifier = DeviceBootIdentifier(12),
                now = origin.plusSeconds(360),
                elapsedRealtime = Duration.ofMinutes(6)
            )
        )

        assertEquals(state, restored)
        assertEquals(Duration.ofMinutes(6), restored.foregroundDurationAt(Duration.ofMinutes(6)))
    }

    @Test
    fun rebootedForegroundSessionDoesNotReceiveFreshAllowance() {
        val checkpoint = SharedSessionCheckpoint.from(
            profile = profile,
            state = activeState(lastObserved = Duration.ofMinutes(4)),
            bootIdentifier = DeviceBootIdentifier(12)
        )

        val restored = requireNotNull(
            SharedSessionRestorer().restore(
                checkpoint = checkpoint,
                profile = profile,
                currentBootIdentifier = DeviceBootIdentifier(13),
                now = origin.plusSeconds(600),
                elapsedRealtime = Duration.ofMinutes(1)
            )
        )

        assertNull(restored.foregroundApplication)
        assertEquals(Duration.ofMinutes(10), restored.accumulatedForegroundDuration)
        assertEquals(Duration.ofMinutes(1), restored.inactiveSinceElapsedRealtime)
        assertTrue(restored.foregroundDurationAt(Duration.ofMinutes(1)) >= Duration.ofMinutes(10))
    }

    @Test
    fun profileMismatchRejectsCheckpoint() {
        val checkpoint = SharedSessionCheckpoint.from(
            profile = profile,
            state = activeState(lastObserved = Duration.ofMinutes(4)),
            bootIdentifier = DeviceBootIdentifier(12)
        )

        val restored = SharedSessionRestorer().restore(
            checkpoint = checkpoint,
            profile = profile.copy(version = 2),
            currentBootIdentifier = DeviceBootIdentifier(12),
            now = origin.plusSeconds(360),
            elapsedRealtime = Duration.ofMinutes(6)
        )

        assertNull(restored)
    }

    private fun activeState(lastObserved: Duration): SharedSessionState.Active = SharedSessionState.Active(
        startedAt = origin,
        startedAtElapsedRealtime = Duration.ZERO,
        accumulatedForegroundDuration = Duration.ZERO,
        foregroundApplication = tikTok,
        foregroundSinceElapsedRealtime = Duration.ZERO,
        inactiveSinceElapsedRealtime = null,
        lastObservedAt = origin.plusMillis(lastObserved.toMillis()),
        lastObservedElapsedRealtime = lastObserved
    )
}
