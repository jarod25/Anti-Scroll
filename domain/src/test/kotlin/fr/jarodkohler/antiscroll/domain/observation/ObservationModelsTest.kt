package fr.jarodkohler.antiscroll.domain.observation

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationModelsTest {
    private val start = Instant.parse("2026-07-27T20:00:00Z")
    private val end = start.plusSeconds(60)
    private val packageName = ApplicationPackageName("com.zhiliaoapp.musically")

    @Test
    fun `observation window is start inclusive and end exclusive`() {
        val window = ObservationWindow(start, end)

        assertTrue(start in window)
        assertTrue(start.plusSeconds(59) in window)
        assertFalse(end in window)
    }

    @Test
    fun `observation window rejects empty ranges`() {
        assertThrows(IllegalArgumentException::class.java) {
            ObservationWindow(start, start)
        }
    }

    @Test
    fun `collected events must match their source and window`() {
        val window = ObservationWindow(start, end)
        val event = usageEvent(occurredAt = start)

        UsageCollectionResult.Collected(
            source = UsageEventSource.USAGE_STATS,
            window = window,
            events = listOf(event),
            completeness = DataCompleteness.COMPLETE
        )

        assertThrows(IllegalArgumentException::class.java) {
            UsageCollectionResult.Collected(
                source = UsageEventSource.ACCESSIBILITY,
                window = window,
                events = listOf(event),
                completeness = DataCompleteness.COMPLETE
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            UsageCollectionResult.Collected(
                source = UsageEventSource.USAGE_STATS,
                window = window,
                events = listOf(usageEvent(occurredAt = end)),
                completeness = DataCompleteness.COMPLETE
            )
        }
    }

    @Test
    fun `partial collection requires an explicit reason`() {
        val window = ObservationWindow(start, end)

        assertThrows(IllegalArgumentException::class.java) {
            UsageCollectionResult.Collected(
                source = UsageEventSource.USAGE_STATS,
                window = window,
                events = emptyList(),
                completeness = DataCompleteness.PARTIAL
            )
        }
    }

    @Test
    fun `daily usage rejects negative values`() {
        assertThrows(IllegalArgumentException::class.java) {
            DailyApplicationUsage(
                date = LocalDate.of(2026, 7, 27),
                packageName = packageName,
                foregroundDuration = Duration.ofSeconds(-1),
                estimatedOpeningCount = 0,
                completeness = DataCompleteness.UNKNOWN
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            DailyApplicationUsage(
                date = LocalDate.of(2026, 7, 27),
                packageName = packageName,
                foregroundDuration = Duration.ZERO,
                estimatedOpeningCount = -1,
                completeness = DataCompleteness.UNKNOWN
            )
        }
    }

    @Test
    fun `collection gap rejects an invalid closed range`() {
        assertThrows(IllegalArgumentException::class.java) {
            CollectionGap(
                source = UsageEventSource.USAGE_STATS,
                startInclusive = start,
                endExclusive = start,
                reason = CollectionGapReason.HISTORY_INCOMPLETE,
                detectedAt = end
            )
        }
    }

    @Test
    fun `healthy monitoring requires usage access and a successful reconciliation`() {
        assertThrows(IllegalArgumentException::class.java) {
            MonitoringHealth(
                usageAccessStatus = UsageAccessStatus.MISSING,
                accessibilityStatus = AccessibilityMonitoringStatus.DISABLED,
                collectionStatus = CollectionStatus.HEALTHY,
                lastSuccessfulReconciliationAt = end
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MonitoringHealth(
                usageAccessStatus = UsageAccessStatus.GRANTED,
                accessibilityStatus = AccessibilityMonitoringStatus.DISABLED,
                collectionStatus = CollectionStatus.HEALTHY,
                lastSuccessfulReconciliationAt = null
            )
        }
    }

    @Test
    fun `append result rejects negative counts`() {
        assertThrows(IllegalArgumentException::class.java) {
            UsageEventAppendResult(insertedCount = -1, duplicateCount = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            UsageEventAppendResult(insertedCount = 0, duplicateCount = -1)
        }
    }

    private fun usageEvent(occurredAt: Instant): NormalizedUsageEvent = NormalizedUsageEvent(
        id = UsageEventId("usage-stats:${occurredAt.toEpochMilli()}"),
        packageName = packageName,
        type = UsageEventType.FOREGROUND_ENTERED,
        occurredAt = occurredAt,
        source = UsageEventSource.USAGE_STATS,
        reliability = UsageEventReliability.OBSERVED
    )
}
