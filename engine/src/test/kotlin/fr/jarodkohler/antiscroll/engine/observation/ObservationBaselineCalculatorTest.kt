package fr.jarodkohler.antiscroll.engine.observation

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.application.MonitoredApplication
import fr.jarodkohler.antiscroll.domain.observation.DailyApplicationUsage
import fr.jarodkohler.antiscroll.domain.observation.DataCompleteness
import fr.jarodkohler.antiscroll.domain.observation.ObservationBaselineStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObservationBaselineCalculatorTest {
    private val calculator = ObservationBaselineCalculator(
        ObservationBaselinePolicy(requiredReliableDays = 3)
    )
    private val currentDate = LocalDate.of(2026, 7, 29)
    private val packageName = ApplicationPackageName("com.zhiliaoapp.musically")

    @Test
    fun noEnabledApplicationDoesNotStartObservation() {
        val baseline = calculator.calculate(
            monitoredApplications = emptyList(),
            dailyUsage = emptyList(),
            currentDate = currentDate,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(ObservationBaselineStatus.NOT_STARTED, baseline.status)
        assertEquals(0, baseline.reliableDayCount)
        assertNull(baseline.observationStartedOn)
    }

    @Test
    fun partialAndCurrentDaysDoNotAdvanceProgress() {
        val application = application(Instant.parse("2026-07-25T00:00:00Z"))
        val baseline = calculator.calculate(
            monitoredApplications = listOf(application),
            dailyUsage = listOf(
                usage(LocalDate.of(2026, 7, 25), 10, DataCompleteness.COMPLETE),
                usage(LocalDate.of(2026, 7, 26), 20, DataCompleteness.PARTIAL),
                usage(LocalDate.of(2026, 7, 27), 30, DataCompleteness.COMPLETE),
                usage(currentDate, 999, DataCompleteness.COMPLETE)
            ),
            currentDate = currentDate,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(ObservationBaselineStatus.COLLECTING, baseline.status)
        assertEquals(2, baseline.reliableDayCount)
        assertEquals(1, baseline.remainingReliableDays)
    }

    @Test
    fun firstReliableDaysProduceStableMedianBaseline() {
        val application = application(Instant.parse("2026-07-25T00:00:00Z"))
        val baseline = calculator.calculate(
            monitoredApplications = listOf(application),
            dailyUsage = listOf(
                usage(LocalDate.of(2026, 7, 25), 10, DataCompleteness.COMPLETE, openings = 2),
                usage(LocalDate.of(2026, 7, 26), 90, DataCompleteness.COMPLETE, openings = 20),
                usage(LocalDate.of(2026, 7, 27), 20, DataCompleteness.COMPLETE, openings = 4),
                usage(LocalDate.of(2026, 7, 28), 500, DataCompleteness.COMPLETE, openings = 100)
            ),
            currentDate = currentDate,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(ObservationBaselineStatus.READY, baseline.status)
        assertEquals(Duration.ofMinutes(20), baseline.typicalGlobalForegroundDuration)
        assertEquals(4, baseline.typicalGlobalOpeningCount)
        assertEquals(Duration.ofMinutes(20), baseline.applications.single().typicalForegroundDuration)
        assertEquals(LocalDate.of(2026, 7, 27), baseline.baselineCompletedOn)
    }

    @Test
    fun newestEnabledApplicationDefinesObservationStart() {
        val first = application(Instant.parse("2026-07-20T00:00:00Z"))
        val secondPackage = ApplicationPackageName("com.instagram.android")
        val second = MonitoredApplication(
            packageName = secondPackage,
            isEnabled = true,
            addedAt = Instant.parse("2026-07-26T00:00:00Z")
        )
        val usage = listOf(
            DailyApplicationUsage(
                date = LocalDate.of(2026, 7, 25),
                packageName = packageName,
                foregroundDuration = Duration.ofMinutes(30),
                estimatedOpeningCount = 3,
                completeness = DataCompleteness.COMPLETE
            ),
            DailyApplicationUsage(
                date = LocalDate.of(2026, 7, 26),
                packageName = packageName,
                foregroundDuration = Duration.ofMinutes(30),
                estimatedOpeningCount = 3,
                completeness = DataCompleteness.COMPLETE
            ),
            DailyApplicationUsage(
                date = LocalDate.of(2026, 7, 26),
                packageName = secondPackage,
                foregroundDuration = Duration.ofMinutes(10),
                estimatedOpeningCount = 1,
                completeness = DataCompleteness.COMPLETE
            )
        )

        val baseline = calculator.calculate(
            monitoredApplications = listOf(first, second),
            dailyUsage = usage,
            currentDate = currentDate,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(LocalDate.of(2026, 7, 26), baseline.observationStartedOn)
        assertEquals(1, baseline.reliableDayCount)
    }

    private fun application(addedAt: Instant): MonitoredApplication = MonitoredApplication(
        packageName = packageName,
        isEnabled = true,
        addedAt = addedAt
    )

    private fun usage(
        date: LocalDate,
        minutes: Long,
        completeness: DataCompleteness,
        openings: Int = 1
    ): DailyApplicationUsage = DailyApplicationUsage(
        date = date,
        packageName = packageName,
        foregroundDuration = Duration.ofMinutes(minutes),
        estimatedOpeningCount = openings,
        completeness = completeness
    )
}
