package fr.jarodkohler.antiscroll.engine.observation

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.observation.NormalizedUsageEvent
import fr.jarodkohler.antiscroll.domain.observation.ObservationWindow
import fr.jarodkohler.antiscroll.domain.observation.UsageEventId
import fr.jarodkohler.antiscroll.domain.observation.UsageEventReliability
import fr.jarodkohler.antiscroll.domain.observation.UsageEventSource
import fr.jarodkohler.antiscroll.domain.observation.UsageEventType
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageSessionReconstructorTest {
    private val packageName = ApplicationPackageName("com.zhiliaoapp.musically")
    private val window = ObservationWindow(
        startInclusive = Instant.parse("2026-07-28T10:00:00Z"),
        endExclusive = Instant.parse("2026-07-28T11:00:00Z")
    )
    private val reconstructor = UsageSessionReconstructor(
        UsageSessionReconstructionPolicy(
            source = UsageEventSource.USAGE_STATS,
            internalTransitionGrace = Duration.ofSeconds(3),
            openingContinuationGrace = Duration.ofMinutes(2),
            boundaryLookback = Duration.ofHours(6)
        )
    )

    @Test
    fun closeInternalActivityTransitionRemainsOnePackageSession() {
        val sessions = reconstructor.reconstruct(
            packageName = packageName,
            window = window,
            events = listOf(
                event("2026-07-28T10:01:00Z", UsageEventType.FOREGROUND_ENTERED, "SplashActivity"),
                event("2026-07-28T10:01:01Z", UsageEventType.FOREGROUND_EXITED, "SplashActivity"),
                event("2026-07-28T10:01:01.100Z", UsageEventType.FOREGROUND_ENTERED, "MainActivity"),
                event("2026-07-28T10:10:00Z", UsageEventType.FOREGROUND_EXITED, "MainActivity")
            ),
            eventBeforeWindow = null
        )

        assertEquals(1, sessions.size)
        assertEquals(Instant.parse("2026-07-28T10:01:00Z"), sessions.single().startInclusive)
        assertEquals(Instant.parse("2026-07-28T10:10:00Z"), sessions.single().endExclusive)
    }

    @Test
    fun overlappingActivitiesKeepPackageForegroundUntilLastActivityExits() {
        val sessions = reconstructor.reconstruct(
            packageName = packageName,
            window = window,
            events = listOf(
                event("2026-07-28T10:01:00Z", UsageEventType.FOREGROUND_ENTERED, "FeedActivity"),
                event("2026-07-28T10:02:00Z", UsageEventType.FOREGROUND_ENTERED, "PlayerActivity"),
                event("2026-07-28T10:03:00Z", UsageEventType.FOREGROUND_EXITED, "FeedActivity"),
                event("2026-07-28T10:04:00Z", UsageEventType.FOREGROUND_EXITED, "PlayerActivity")
            ),
            eventBeforeWindow = null
        )

        assertEquals(1, sessions.size)
        assertEquals(Instant.parse("2026-07-28T10:04:00Z"), sessions.single().endExclusive)
    }

    @Test
    fun foregroundEntryAfterGraceStartsANewSession() {
        val sessions = reconstructor.reconstruct(
            packageName = packageName,
            window = window,
            events = listOf(
                event("2026-07-28T10:01:00Z", UsageEventType.FOREGROUND_ENTERED, "MainActivity"),
                event("2026-07-28T10:05:00Z", UsageEventType.FOREGROUND_EXITED, "MainActivity"),
                event("2026-07-28T10:05:10Z", UsageEventType.FOREGROUND_ENTERED, "MainActivity"),
                event("2026-07-28T10:07:00Z", UsageEventType.FOREGROUND_EXITED, "MainActivity")
            ),
            eventBeforeWindow = null
        )

        assertEquals(2, sessions.size)
        assertEquals(Instant.parse("2026-07-28T10:05:00Z"), sessions.first().endExclusive)
        assertEquals(Instant.parse("2026-07-28T10:05:10Z"), sessions.last().startInclusive)
    }

    @Test
    fun foregroundStateBeforeWindowCreatesAnInferredBoundary() {
        val seed = event(
            instant = "2026-07-28T09:59:00Z",
            type = UsageEventType.FOREGROUND_ENTERED,
            activityClassName = "MainActivity"
        )
        val sessions = reconstructor.reconstruct(
            packageName = packageName,
            window = window,
            events = listOf(
                event("2026-07-28T10:05:00Z", UsageEventType.FOREGROUND_EXITED, "MainActivity")
            ),
            eventBeforeWindow = seed
        )

        assertEquals(window.startInclusive, sessions.single().startInclusive)
        assertTrue(sessions.single().startInferred)
        assertFalse(sessions.single().endInferred)
    }

    @Test
    fun legacyEventsWithoutActivityMetadataUsePackageFallback() {
        val sessions = reconstructor.reconstruct(
            packageName = packageName,
            window = window,
            events = listOf(
                event("2026-07-28T10:01:00Z", UsageEventType.FOREGROUND_ENTERED, null),
                event("2026-07-28T10:01:01Z", UsageEventType.FOREGROUND_EXITED, null),
                event("2026-07-28T10:01:02Z", UsageEventType.FOREGROUND_ENTERED, null),
                event("2026-07-28T10:05:00Z", UsageEventType.FOREGROUND_EXITED, null)
            ),
            eventBeforeWindow = null
        )

        assertEquals(1, sessions.size)
        assertEquals(Instant.parse("2026-07-28T10:05:00Z"), sessions.single().endExclusive)
    }

    @Test
    fun openSessionIsClampedToRequestedWindow() {
        val sessions = reconstructor.reconstruct(
            packageName = packageName,
            window = window,
            events = listOf(
                event("2026-07-28T10:55:00Z", UsageEventType.FOREGROUND_ENTERED, "MainActivity")
            ),
            eventBeforeWindow = null
        )

        assertEquals(window.endExclusive, sessions.single().endExclusive)
        assertTrue(sessions.single().endInferred)
    }

    private fun event(instant: String, type: UsageEventType, activityClassName: String?): NormalizedUsageEvent {
        val occurredAt = Instant.parse(instant)
        return NormalizedUsageEvent(
            id = UsageEventId("${type.name}-${occurredAt.toEpochMilli()}-${activityClassName.orEmpty()}"),
            packageName = packageName,
            type = type,
            occurredAt = occurredAt,
            source = UsageEventSource.USAGE_STATS,
            reliability = UsageEventReliability.OBSERVED,
            activityClassName = activityClassName
        )
    }
}
