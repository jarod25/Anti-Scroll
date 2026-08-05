package fr.jarodkohler.antiscroll.restriction

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionEvent
import fr.jarodkohler.antiscroll.monitoring.usagestats.UsageStatsActivityEventType
import fr.jarodkohler.antiscroll.monitoring.usagestats.UsageStatsEventRecord
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageStatsSharedSessionEventTrackerTest {
    private val tikTok = ApplicationPackageName("com.zhiliaoapp.musically")
    private val origin = Instant.parse("2026-08-05T10:00:00Z")
    private val snapshot = ReconciliationClockSnapshot(
        observedAt = origin.plusSeconds(20),
        elapsedRealtime = Duration.ofHours(1)
    )

    @Test
    fun accessibilityHintBecomesExplicitBackgroundCorrection() {
        val tracker = UsageStatsSharedSessionEventTracker()
        tracker.markForeground(tikTok)

        val events = tracker.acceptSettled(
            records = listOf(record(UsageStatsActivityEventType.PAUSED, second = 10, activity = "FeedActivity"))
        )

        assertEquals(1, events.size)
        val backgrounded = events.single() as SharedSessionEvent.ApplicationBackgrounded
        assertEquals(tikTok, backgrounded.packageName)
        assertEquals(origin.plusSeconds(10), backgrounded.observedAt)
        assertEquals(Duration.ofHours(1).minusSeconds(10), backgrounded.elapsedRealtime)
    }

    @Test
    fun unmatchedPauseRemainsReplayableUntilAccessibilityHintArrives() {
        val tracker = UsageStatsSharedSessionEventTracker()
        val paused = record(UsageStatsActivityEventType.PAUSED, second = 10, activity = "FeedActivity")

        val beforeHint = tracker.acceptSettled(records = listOf(paused))
        tracker.markForeground(tikTok)
        val afterHint = tracker.acceptSettled(records = listOf(paused))

        assertTrue(beforeHint.isEmpty())
        assertTrue(afterHint.single() is SharedSessionEvent.ApplicationBackgrounded)
    }

    @Test
    fun activityReplacementWithinSettlementWindowDoesNotPausePackage() {
        val tracker = UsageStatsSharedSessionEventTracker()
        tracker.markForeground(tikTok)

        val events = tracker.acceptSettled(
            records = listOf(
                record(UsageStatsActivityEventType.PAUSED, second = 10, activity = "FeedActivity"),
                record(UsageStatsActivityEventType.RESUMED, second = 11, activity = "ShareActivity")
            )
        )

        assertTrue(events.isEmpty())
    }

    @Test
    fun overlappingWindowsDoNotEmitDuplicateEvents() {
        val tracker = UsageStatsSharedSessionEventTracker()
        val resumed = record(UsageStatsActivityEventType.RESUMED, second = 5, activity = "FeedActivity")

        val first = tracker.acceptSettled(records = listOf(resumed))
        val second = tracker.acceptSettled(records = listOf(resumed))

        assertEquals(1, first.size)
        assertTrue(first.single() is SharedSessionEvent.ApplicationForegrounded)
        assertTrue(second.isEmpty())
    }

    @Test
    fun packageStaysForegroundUntilItsLastActivityPauses() {
        val tracker = UsageStatsSharedSessionEventTracker()

        val entered = tracker.acceptSettled(
            records = listOf(
                record(UsageStatsActivityEventType.RESUMED, second = 1, activity = "FeedActivity"),
                record(UsageStatsActivityEventType.RESUMED, second = 2, activity = "ShareActivity")
            )
        )
        val firstPause = tracker.acceptSettled(
            records = listOf(record(UsageStatsActivityEventType.PAUSED, second = 3, activity = "FeedActivity"))
        )
        val finalPause = tracker.acceptSettled(
            records = listOf(record(UsageStatsActivityEventType.PAUSED, second = 4, activity = "ShareActivity"))
        )

        assertTrue(entered.single() is SharedSessionEvent.ApplicationForegrounded)
        assertTrue(firstPause.isEmpty())
        assertTrue(finalPause.single() is SharedSessionEvent.ApplicationBackgrounded)
    }

    @Test
    fun unsettledRecentEventsAreRetriedLater() {
        val tracker = UsageStatsSharedSessionEventTracker()
        val resumed = record(UsageStatsActivityEventType.RESUMED, second = 20, activity = "FeedActivity")

        val unsettled = tracker.accept(
            records = listOf(resumed),
            allowedPackages = setOf(tikTok),
            clockSnapshot = snapshot,
            retainFrom = origin,
            processThrough = origin.plusSeconds(19),
            settlementWindow = Duration.ofSeconds(1)
        )
        val laterSnapshot = ReconciliationClockSnapshot(
            observedAt = origin.plusSeconds(22),
            elapsedRealtime = Duration.ofHours(1).plusSeconds(2)
        )
        val settled = tracker.accept(
            records = listOf(resumed),
            allowedPackages = setOf(tikTok),
            clockSnapshot = laterSnapshot,
            retainFrom = origin,
            processThrough = origin.plusSeconds(21),
            settlementWindow = Duration.ofSeconds(1)
        )

        assertTrue(unsettled.isEmpty())
        assertTrue(settled.single() is SharedSessionEvent.ApplicationForegrounded)
    }

    @Test
    fun eventsOutsideTheMonotonicClockRangeAreIgnored() {
        val tracker = UsageStatsSharedSessionEventTracker()
        val future = UsageStatsEventRecord(
            packageName = tikTok.value,
            activityClassName = "FeedActivity",
            eventType = UsageStatsActivityEventType.RESUMED,
            occurredAtEpochMillis = snapshot.observedAt.plusSeconds(1).toEpochMilli()
        )
        val beforeBoot = UsageStatsEventRecord(
            packageName = tikTok.value,
            activityClassName = "FeedActivity",
            eventType = UsageStatsActivityEventType.RESUMED,
            occurredAtEpochMillis = snapshot.observedAt.minus(Duration.ofHours(2)).toEpochMilli()
        )

        val events = tracker.accept(
            records = listOf(future, beforeBoot),
            allowedPackages = setOf(tikTok),
            clockSnapshot = snapshot,
            retainFrom = snapshot.observedAt.minus(Duration.ofHours(3)),
            processThrough = snapshot.observedAt,
            settlementWindow = Duration.ZERO
        )

        assertTrue(events.isEmpty())
    }

    private fun UsageStatsSharedSessionEventTracker.acceptSettled(
        records: Collection<UsageStatsEventRecord>
    ): List<SharedSessionEvent> = accept(
        records = records,
        allowedPackages = setOf(tikTok),
        clockSnapshot = snapshot,
        retainFrom = origin,
        processThrough = snapshot.observedAt.minusSeconds(1),
        settlementWindow = Duration.ofSeconds(1)
    )

    private fun record(type: UsageStatsActivityEventType, second: Long, activity: String): UsageStatsEventRecord =
        UsageStatsEventRecord(
            packageName = tikTok.value,
            activityClassName = activity,
            eventType = type,
            occurredAtEpochMillis = origin.plusSeconds(second).toEpochMilli()
        )
}
