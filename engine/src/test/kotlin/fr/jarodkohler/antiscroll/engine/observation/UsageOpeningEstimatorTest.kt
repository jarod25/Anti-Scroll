package fr.jarodkohler.antiscroll.engine.observation

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.observation.ObservationWindow
import fr.jarodkohler.antiscroll.domain.observation.UsageEventSource
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageOpeningEstimatorTest {
    private val tikTok = ApplicationPackageName("com.zhiliaoapp.musically")
    private val instagram = ApplicationPackageName("com.instagram.android")
    private val window = ObservationWindow(
        startInclusive = Instant.parse("2026-07-29T00:00:00Z"),
        endExclusive = Instant.parse("2026-07-30T00:00:00Z")
    )
    private val estimator = UsageOpeningEstimator(
        UsageSessionReconstructionPolicy(
            source = UsageEventSource.USAGE_STATS,
            internalTransitionGrace = Duration.ofSeconds(3),
            openingContinuationGrace = Duration.ofMinutes(2),
            boundaryLookback = Duration.ofHours(6)
        )
    )

    @Test
    fun briefReturnWithoutAnotherMonitoredAppRemainsOneOpening() {
        val sessions = listOf(
            session(tikTok, "2026-07-29T10:00:00Z", "2026-07-29T10:05:00Z"),
            session(tikTok, "2026-07-29T10:05:45Z", "2026-07-29T10:10:00Z")
        )

        val openings = estimator.estimate(
            packageName = tikTok,
            targetWindow = window,
            sessionsByPackage = mapOf(tikTok to sessions)
        )

        assertEquals(1, openings)
    }

    @Test
    fun monitoredScrollAppDuringGapStartsANewOpening() {
        val tikTokSessions = listOf(
            session(tikTok, "2026-07-29T10:00:00Z", "2026-07-29T10:05:00Z"),
            session(tikTok, "2026-07-29T10:06:00Z", "2026-07-29T10:10:00Z")
        )
        val instagramSessions = listOf(
            session(instagram, "2026-07-29T10:05:05Z", "2026-07-29T10:05:55Z")
        )

        val openings = estimator.estimate(
            packageName = tikTok,
            targetWindow = window,
            sessionsByPackage = mapOf(
                tikTok to tikTokSessions,
                instagram to instagramSessions
            )
        )

        assertEquals(2, openings)
    }

    @Test
    fun returnAfterContinuationWindowStartsANewOpening() {
        val sessions = listOf(
            session(tikTok, "2026-07-29T10:00:00Z", "2026-07-29T10:05:00Z"),
            session(tikTok, "2026-07-29T10:08:00Z", "2026-07-29T10:10:00Z")
        )

        val openings = estimator.estimate(
            packageName = tikTok,
            targetWindow = window,
            sessionsByPackage = mapOf(tikTok to sessions)
        )

        assertEquals(2, openings)
    }

    private fun session(
        packageName: ApplicationPackageName,
        startInclusive: String,
        endExclusive: String
    ): ReconstructedUsageSession = ReconstructedUsageSession(
        packageName = packageName,
        startInclusive = Instant.parse(startInclusive),
        endExclusive = Instant.parse(endExclusive),
        startInferred = false,
        endInferred = false
    )
}
