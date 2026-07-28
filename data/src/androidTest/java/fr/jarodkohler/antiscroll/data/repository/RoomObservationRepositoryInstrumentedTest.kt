package fr.jarodkohler.antiscroll.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.jarodkohler.antiscroll.data.local.AntiScrollDatabase
import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.observation.AccessibilityMonitoringStatus
import fr.jarodkohler.antiscroll.domain.observation.CollectionCheckpoint
import fr.jarodkohler.antiscroll.domain.observation.CollectionGap
import fr.jarodkohler.antiscroll.domain.observation.CollectionGapReason
import fr.jarodkohler.antiscroll.domain.observation.CollectionStatus
import fr.jarodkohler.antiscroll.domain.observation.MonitoringHealth
import fr.jarodkohler.antiscroll.domain.observation.NormalizedUsageEvent
import fr.jarodkohler.antiscroll.domain.observation.ObservationWindow
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus
import fr.jarodkohler.antiscroll.domain.observation.UsageEventId
import fr.jarodkohler.antiscroll.domain.observation.UsageEventReliability
import fr.jarodkohler.antiscroll.domain.observation.UsageEventSource
import fr.jarodkohler.antiscroll.domain.observation.UsageEventType
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomObservationRepositoryInstrumentedTest {
    private lateinit var database: AntiScrollDatabase
    private lateinit var repository: RoomObservationRepository

    private val packageName = ApplicationPackageName("com.instagram.android")
    private val window = ObservationWindow(
        startInclusive = Instant.ofEpochMilli(1_000L),
        endExclusive = Instant.ofEpochMilli(5_000L)
    )

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AntiScrollDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = RoomObservationRepository(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun eventJournalIsIdempotentAndQueryable() = runBlocking {
        val event = event(id = "event-1", occurredAtEpochMillis = 2_000L)

        val appendResult = repository.append(listOf(event, event))
        val storedEvents = repository.eventsIn(window, setOf(packageName))

        assertEquals(1, appendResult.insertedCount)
        assertEquals(1, appendResult.duplicateCount)
        assertEquals(listOf(event), storedEvents)
        assertEquals(emptyList<NormalizedUsageEvent>(), repository.eventsIn(window, emptySet()))
    }

    @Test
    fun eventsAndCheckpointAreCommittedTogetherAndCanBeReplayed() = runBlocking {
        val event = event(id = "event-2", occurredAtEpochMillis = 3_000L)
        val checkpoint = CollectionCheckpoint(
            source = UsageEventSource.USAGE_STATS,
            reconciledThrough = Instant.ofEpochMilli(5_000L),
            updatedAt = Instant.ofEpochMilli(5_100L)
        )

        val firstResult = repository.appendAndCheckpoint(listOf(event), checkpoint)
        val replayResult = repository.appendAndCheckpoint(listOf(event), checkpoint)

        assertEquals(1, firstResult.insertedCount)
        assertEquals(0, firstResult.duplicateCount)
        assertEquals(0, replayResult.insertedCount)
        assertEquals(1, replayResult.duplicateCount)
        assertEquals(checkpoint, repository.checkpoint(UsageEventSource.USAGE_STATS))
        assertNull(repository.checkpoint(UsageEventSource.ACCESSIBILITY))
    }

    @Test
    fun healthAndOverlappingGapsRemainObservable() = runBlocking {
        assertEquals(CollectionStatus.NOT_STARTED, repository.observeHealth().first().collectionStatus)

        val health = MonitoringHealth(
            usageAccessStatus = UsageAccessStatus.GRANTED,
            accessibilityStatus = AccessibilityMonitoringStatus.DISABLED,
            collectionStatus = CollectionStatus.HEALTHY,
            lastSuccessfulReconciliationAt = Instant.ofEpochMilli(4_500L)
        )
        repository.saveHealth(health)
        assertEquals(health, repository.observeHealth().first())

        val overlappingGap = CollectionGap(
            source = UsageEventSource.USAGE_STATS,
            startInclusive = Instant.ofEpochMilli(2_000L),
            endExclusive = Instant.ofEpochMilli(3_000L),
            reason = CollectionGapReason.DEVICE_LOCKED,
            detectedAt = Instant.ofEpochMilli(3_100L)
        )
        val outsideGap = CollectionGap(
            source = UsageEventSource.USAGE_STATS,
            startInclusive = Instant.ofEpochMilli(6_000L),
            endExclusive = Instant.ofEpochMilli(7_000L),
            reason = CollectionGapReason.SOURCE_UNAVAILABLE,
            detectedAt = Instant.ofEpochMilli(7_100L)
        )
        repository.recordGap(overlappingGap)
        repository.recordGap(outsideGap)

        assertEquals(listOf(overlappingGap), repository.observeGaps(window).first())
    }

    private fun event(id: String, occurredAtEpochMillis: Long): NormalizedUsageEvent = NormalizedUsageEvent(
        id = UsageEventId(id),
        packageName = packageName,
        type = UsageEventType.FOREGROUND_ENTERED,
        occurredAt = Instant.ofEpochMilli(occurredAtEpochMillis),
        source = UsageEventSource.USAGE_STATS,
        reliability = UsageEventReliability.OBSERVED
    )
}
