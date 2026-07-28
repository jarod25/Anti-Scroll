package fr.jarodkohler.antiscroll.engine.observation

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.application.MonitoredApplication
import fr.jarodkohler.antiscroll.domain.observation.AccessibilityMonitoringStatus
import fr.jarodkohler.antiscroll.domain.observation.CollectionCheckpoint
import fr.jarodkohler.antiscroll.domain.observation.CollectionGap
import fr.jarodkohler.antiscroll.domain.observation.CollectionGapReason
import fr.jarodkohler.antiscroll.domain.observation.CollectionStatus
import fr.jarodkohler.antiscroll.domain.observation.DataCompleteness
import fr.jarodkohler.antiscroll.domain.observation.MonitoredApplicationRepository
import fr.jarodkohler.antiscroll.domain.observation.MonitoringHealth
import fr.jarodkohler.antiscroll.domain.observation.NormalizedUsageEvent
import fr.jarodkohler.antiscroll.domain.observation.ObservationCommitRepository
import fr.jarodkohler.antiscroll.domain.observation.ObservationStateRepository
import fr.jarodkohler.antiscroll.domain.observation.ObservationWindow
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus
import fr.jarodkohler.antiscroll.domain.observation.UsageCollectionResult
import fr.jarodkohler.antiscroll.domain.observation.UsageEventAppendResult
import fr.jarodkohler.antiscroll.domain.observation.UsageEventId
import fr.jarodkohler.antiscroll.domain.observation.UsageEventReliability
import fr.jarodkohler.antiscroll.domain.observation.UsageEventRepository
import fr.jarodkohler.antiscroll.domain.observation.UsageEventSource
import fr.jarodkohler.antiscroll.domain.observation.UsageEventType
import fr.jarodkohler.antiscroll.domain.observation.UsageObservationSource
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationReconciliationCoordinatorTest {
    @Test
    fun firstCompleteCollectionPersistsEventsCheckpointAndHealthyState() = runTest {
        val fixture = Fixture()
        fixture.source.collector = { window, packageNames ->
            assertEquals(fixture.now.minus(Duration.ofHours(24)), window.startInclusive)
            assertEquals(fixture.now, window.endExclusive)
            assertEquals(setOf(fixture.packageName), packageNames)
            UsageCollectionResult.Collected(
                source = UsageEventSource.USAGE_STATS,
                window = window,
                events = listOf(fixture.event(window.startInclusive.plusSeconds(10))),
                completeness = DataCompleteness.COMPLETE
            )
        }

        val report = fixture.coordinator().reconcile()

        assertFalse(report.shouldRetry)
        assertEquals(1, report.insertedEventCount)
        assertEquals(CollectionStatus.HEALTHY, report.health.collectionStatus)
        assertEquals(UsageAccessStatus.GRANTED, report.health.usageAccessStatus)
        assertEquals(fixture.now, report.health.lastSuccessfulReconciliationAt)
        assertEquals(fixture.now, fixture.commitRepository.savedCheckpoint?.reconciledThrough)
        assertEquals(fixture.now, fixture.stateRepository.currentHealth.lastSuccessfulReconciliationAt)
    }

    @Test
    fun existingCheckpointReplaysConfiguredOverlap() = runTest {
        val fixture = Fixture()
        val checkpoint = CollectionCheckpoint(
            source = UsageEventSource.USAGE_STATS,
            reconciledThrough = fixture.now.minus(Duration.ofHours(1)),
            updatedAt = fixture.now.minus(Duration.ofHours(1))
        )
        fixture.stateRepository.checkpoints[UsageEventSource.USAGE_STATS] = checkpoint
        fixture.source.collector = { window, _ ->
            assertEquals(
                checkpoint.reconciledThrough.minus(Duration.ofMinutes(5)),
                window.startInclusive
            )
            UsageCollectionResult.Collected(
                source = UsageEventSource.USAGE_STATS,
                window = window,
                events = emptyList(),
                completeness = DataCompleteness.COMPLETE
            )
        }

        val report = fixture.coordinator().reconcile()

        assertFalse(report.shouldRetry)
        assertEquals(SourceReconciliationStatus.COMPLETE, report.sourceOutcomes.single().status)
    }

    @Test
    fun missingUsageAccessRecordsGapWithoutRequestingImmediateRetry() = runTest {
        val fixture = Fixture()
        fixture.source.collector = { window, _ ->
            UsageCollectionResult.Unavailable(
                source = UsageEventSource.USAGE_STATS,
                window = window,
                reason = CollectionGapReason.USAGE_ACCESS_MISSING
            )
        }

        val report = fixture.coordinator().reconcile()

        assertFalse(report.shouldRetry)
        assertEquals(CollectionStatus.UNAVAILABLE, report.health.collectionStatus)
        assertEquals(UsageAccessStatus.MISSING, report.health.usageAccessStatus)
        assertEquals(CollectionGapReason.USAGE_ACCESS_MISSING, fixture.stateRepository.gaps.single().reason)
        assertNull(fixture.commitRepository.savedCheckpoint)
    }

    @Test
    fun lockedDeviceKeepsAccessGrantedAndRequestsRetry() = runTest {
        val fixture = Fixture()
        fixture.source.collector = { window, _ ->
            UsageCollectionResult.Unavailable(
                source = UsageEventSource.USAGE_STATS,
                window = window,
                reason = CollectionGapReason.DEVICE_LOCKED
            )
        }

        val report = fixture.coordinator().reconcile()

        assertTrue(report.shouldRetry)
        assertEquals(CollectionStatus.DEGRADED, report.health.collectionStatus)
        assertEquals(UsageAccessStatus.GRANTED, report.health.usageAccessStatus)
        assertEquals(CollectionGapReason.DEVICE_LOCKED, fixture.stateRepository.gaps.single().reason)
    }

    @Test
    fun partialCollectionPersistsObservedEventsWithoutAdvancingCheckpoint() = runTest {
        val fixture = Fixture()
        fixture.source.collector = { window, _ ->
            UsageCollectionResult.Collected(
                source = UsageEventSource.USAGE_STATS,
                window = window,
                events = listOf(fixture.event(window.startInclusive.plusSeconds(10))),
                completeness = DataCompleteness.PARTIAL,
                incompleteReason = CollectionGapReason.HISTORY_INCOMPLETE
            )
        }

        val report = fixture.coordinator().reconcile()

        assertFalse(report.shouldRetry)
        assertEquals(SourceReconciliationStatus.PARTIAL, report.sourceOutcomes.single().status)
        assertEquals(1, fixture.usageEventRepository.appendedEvents.size)
        assertNull(fixture.commitRepository.savedCheckpoint)
        assertEquals(CollectionGapReason.HISTORY_INCOMPLETE, fixture.stateRepository.gaps.single().reason)
    }

    @Test
    fun persistenceFailurePreservesPreviousSuccessfulReconciliationAndRetries() = runTest {
        val previousSuccess = Instant.parse("2026-07-27T12:00:00Z")
        val fixture = Fixture(
            initialHealth = MonitoringHealth(
                usageAccessStatus = UsageAccessStatus.GRANTED,
                accessibilityStatus = AccessibilityMonitoringStatus.UNSUPPORTED,
                collectionStatus = CollectionStatus.HEALTHY,
                lastSuccessfulReconciliationAt = previousSuccess
            )
        )
        fixture.commitRepository.failCommit = true
        fixture.source.collector = { window, _ ->
            UsageCollectionResult.Collected(
                source = UsageEventSource.USAGE_STATS,
                window = window,
                events = emptyList(),
                completeness = DataCompleteness.COMPLETE
            )
        }

        val report = fixture.coordinator().reconcile()

        assertTrue(report.shouldRetry)
        assertEquals(SourceReconciliationStatus.PERSISTENCE_FAILED, report.sourceOutcomes.single().status)
        assertEquals(previousSuccess, report.health.lastSuccessfulReconciliationAt)
        assertEquals(CollectionStatus.DEGRADED, report.health.collectionStatus)
    }

    private class Fixture(
        val now: Instant = Instant.parse("2026-07-28T16:00:00Z"),
        initialHealth: MonitoringHealth = MonitoringHealth(
            usageAccessStatus = UsageAccessStatus.UNAVAILABLE,
            accessibilityStatus = AccessibilityMonitoringStatus.UNSUPPORTED,
            collectionStatus = CollectionStatus.NOT_STARTED,
            lastSuccessfulReconciliationAt = null
        )
    ) {
        val packageName = ApplicationPackageName("com.instagram.android")
        val source = FakeUsageObservationSource()
        val usageEventRepository = FakeUsageEventRepository()
        val commitRepository = FakeObservationCommitRepository()
        val stateRepository = FakeObservationStateRepository(initialHealth)
        private val monitoredApplicationRepository = FakeMonitoredApplicationRepository(
            listOf(
                MonitoredApplication(
                    packageName = packageName,
                    isEnabled = true,
                    addedAt = now.minusSeconds(60)
                )
            )
        )

        fun coordinator(): ObservationReconciliationCoordinator = ObservationReconciliationCoordinator(
            sources = setOf(source),
            monitoredApplicationRepository = monitoredApplicationRepository,
            usageEventRepository = usageEventRepository,
            observationCommitRepository = commitRepository,
            observationStateRepository = stateRepository,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            policy = ObservationReconciliationPolicy(
                initialLookback = Duration.ofHours(24),
                replayOverlap = Duration.ofMinutes(5),
                maximumLookback = Duration.ofDays(3)
            )
        )

        fun event(occurredAt: Instant): NormalizedUsageEvent = NormalizedUsageEvent(
            id = UsageEventId("event-${occurredAt.toEpochMilli()}"),
            packageName = packageName,
            type = UsageEventType.FOREGROUND_ENTERED,
            occurredAt = occurredAt,
            source = UsageEventSource.USAGE_STATS,
            reliability = UsageEventReliability.OBSERVED
        )
    }
}

private class FakeUsageObservationSource : UsageObservationSource {
    override val source: UsageEventSource = UsageEventSource.USAGE_STATS
    var collector: suspend (ObservationWindow, Set<ApplicationPackageName>) -> UsageCollectionResult =
        { window, _ ->
            UsageCollectionResult.Collected(
                source = source,
                window = window,
                events = emptyList(),
                completeness = DataCompleteness.COMPLETE
            )
        }

    override suspend fun collect(
        window: ObservationWindow,
        packageNames: Set<ApplicationPackageName>
    ): UsageCollectionResult = collector(window, packageNames)
}

private class FakeMonitoredApplicationRepository(
    private val applications: List<MonitoredApplication>
) : MonitoredApplicationRepository {
    override fun observeAll(): Flow<List<MonitoredApplication>> = flowOf(applications)

    override suspend fun enabledApplications(): List<MonitoredApplication> =
        applications.filter(MonitoredApplication::isEnabled)

    override suspend fun save(application: MonitoredApplication) = Unit
}

private class FakeUsageEventRepository : UsageEventRepository {
    val appendedEvents = mutableListOf<NormalizedUsageEvent>()

    override suspend fun append(events: Collection<NormalizedUsageEvent>): UsageEventAppendResult {
        appendedEvents += events
        return UsageEventAppendResult(insertedCount = events.size, duplicateCount = 0)
    }

    override suspend fun eventsIn(
        window: ObservationWindow,
        packageNames: Set<ApplicationPackageName>
    ): List<NormalizedUsageEvent> = emptyList()
}

private class FakeObservationCommitRepository : ObservationCommitRepository {
    var failCommit = false
    var savedCheckpoint: CollectionCheckpoint? = null

    override suspend fun appendAndCheckpoint(
        events: Collection<NormalizedUsageEvent>,
        checkpoint: CollectionCheckpoint
    ): UsageEventAppendResult {
        if (failCommit) error("Simulated persistence failure")
        savedCheckpoint = checkpoint
        return UsageEventAppendResult(insertedCount = events.size, duplicateCount = 0)
    }
}

private class FakeObservationStateRepository(initialHealth: MonitoringHealth) : ObservationStateRepository {
    private val healthFlow = MutableStateFlow(initialHealth)
    var currentHealth: MonitoringHealth = initialHealth
        private set
    val checkpoints = mutableMapOf<UsageEventSource, CollectionCheckpoint>()
    val gaps = mutableListOf<CollectionGap>()

    override fun observeHealth(): Flow<MonitoringHealth> = healthFlow

    override suspend fun health(): MonitoringHealth = currentHealth

    override suspend fun saveHealth(health: MonitoringHealth) {
        currentHealth = health
        healthFlow.value = health
    }

    override suspend fun checkpoint(source: UsageEventSource): CollectionCheckpoint? = checkpoints[source]

    override suspend fun saveCheckpoint(checkpoint: CollectionCheckpoint) {
        checkpoints[checkpoint.source] = checkpoint
    }

    override fun observeGaps(window: ObservationWindow): Flow<List<CollectionGap>> = flowOf(gaps)

    override suspend fun recordGap(gap: CollectionGap) {
        gaps += gap
    }
}
