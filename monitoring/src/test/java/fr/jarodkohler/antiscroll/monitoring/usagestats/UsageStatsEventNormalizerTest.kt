package fr.jarodkohler.antiscroll.monitoring.usagestats

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.observation.ObservationWindow
import fr.jarodkohler.antiscroll.domain.observation.UsageEventReliability
import fr.jarodkohler.antiscroll.domain.observation.UsageEventSource
import fr.jarodkohler.antiscroll.domain.observation.UsageEventType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UsageStatsEventNormalizerTest {
    private val normalizer = UsageStatsEventNormalizer()
    private val instagramPackage = ApplicationPackageName("com.instagram.android")
    private val tiktokPackage = ApplicationPackageName("com.zhiliaoapp.musically")
    private val window = ObservationWindow(
        startInclusive = Instant.ofEpochMilli(1_000L),
        endExclusive = Instant.ofEpochMilli(5_000L)
    )

    @Test
    fun filtersSortsAndDeduplicatesRecords() {
        val duplicatedTikTokRecord = record(
            packageName = tiktokPackage.value,
            eventType = UsageStatsActivityEventType.RESUMED,
            occurredAtEpochMillis = 3_000L,
            activityClassName = "TikTokActivity"
        )

        val events = normalizer.normalize(
            records = listOf(
                duplicatedTikTokRecord,
                record(
                    packageName = "com.unsupported.application",
                    eventType = UsageStatsActivityEventType.RESUMED,
                    occurredAtEpochMillis = 1_500L
                ),
                record(
                    packageName = instagramPackage.value,
                    eventType = UsageStatsActivityEventType.PAUSED,
                    occurredAtEpochMillis = 2_000L
                ),
                duplicatedTikTokRecord,
                record(
                    packageName = instagramPackage.value,
                    eventType = UsageStatsActivityEventType.RESUMED,
                    occurredAtEpochMillis = 5_000L
                )
            ),
            window = window,
            packageNames = setOf(instagramPackage, tiktokPackage)
        )

        assertEquals(2, events.size)
        assertEquals(instagramPackage, events[0].packageName)
        assertEquals(UsageEventType.FOREGROUND_EXITED, events[0].type)
        assertEquals(Instant.ofEpochMilli(2_000L), events[0].occurredAt)
        assertEquals(tiktokPackage, events[1].packageName)
        assertEquals(UsageEventType.FOREGROUND_ENTERED, events[1].type)
        assertEquals(UsageEventSource.USAGE_STATS, events[1].source)
        assertEquals(UsageEventReliability.OBSERVED, events[1].reliability)
        assertEquals("TikTokActivity", events[1].activityClassName)
    }

    @Test
    fun identifiersAreStableButKeepDistinctActivityClasses() {
        val firstRecord = record(
            packageName = instagramPackage.value,
            eventType = UsageStatsActivityEventType.RESUMED,
            occurredAtEpochMillis = 2_000L,
            activityClassName = "FeedActivity"
        )
        val secondRecord = firstRecord.copy(activityClassName = "SettingsActivity")

        val firstPass = normalizer.normalize(
            records = listOf(firstRecord, secondRecord),
            window = window,
            packageNames = setOf(instagramPackage)
        )
        val secondPass = normalizer.normalize(
            records = listOf(secondRecord, firstRecord),
            window = window,
            packageNames = setOf(instagramPackage)
        )

        assertEquals(firstPass.map { event -> event.id }, secondPass.map { event -> event.id })
        assertNotEquals(firstPass[0].id, firstPass[1].id)
        assertEquals(
            setOf("FeedActivity", "SettingsActivity"),
            firstPass.mapTo(mutableSetOf()) { event -> event.activityClassName }
        )
    }

    private fun record(
        packageName: String,
        eventType: UsageStatsActivityEventType,
        occurredAtEpochMillis: Long,
        activityClassName: String? = null
    ): UsageStatsEventRecord = UsageStatsEventRecord(
        packageName = packageName,
        activityClassName = activityClassName,
        eventType = eventType,
        occurredAtEpochMillis = occurredAtEpochMillis
    )
}
