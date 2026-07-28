package fr.jarodkohler.antiscroll.engine.observation

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.application.MonitoredApplication
import fr.jarodkohler.antiscroll.domain.observation.AccessibilityMonitoringStatus
import fr.jarodkohler.antiscroll.domain.observation.CollectionCheckpoint
import fr.jarodkohler.antiscroll.domain.observation.CollectionGap
import fr.jarodkohler.antiscroll.domain.observation.CollectionGapReason
import fr.jarodkohler.antiscroll.domain.observation.CollectionStatus
import fr.jarodkohler.antiscroll.domain.observation.DailyApplicationUsage
import fr.jarodkohler.antiscroll.domain.observation.DailyUsageRepository
import fr.jarodkohler.antiscroll.domain.observation.DataCompleteness
import fr.jarodkohler.antiscroll.domain.observation.MonitoredApplicationRepository
import fr.jarodkohler.antiscroll.domain.observation.MonitoringHealth
import fr.jarodkohler.antiscroll.domain.observation.NormalizedUsageEvent
import fr.jarodkohler.antiscroll.domain.observation.ObservationStateRepository
import fr.jarodkohler.antiscroll.domain.observation.ObservationWindow
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus
import fr.jarodkohler.antiscroll.domain.observation.UsageEventAppendResult
import fr.jarodkohler.antiscroll.domain.observation.UsageEventId
import fr.jarodkohler.antiscroll.domain.observation.UsageEventReliability
import fr.jarodkohler.antiscroll.domain.observation.UsageEventRepository
import fr.jarodkohler.antiscroll.domain.observation.UsageEventSource
import fr.jarodkohler.antiscroll.domain.observation.UsageEventType
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyUsageProjectionCoordinatorTest {
    private val packageName = ApplicationPackageName("com.zhiliaoapp.musically")
    private val zoneId = ZoneId.of("UTC")
    private val policy = UsageSessionReconstructionPolicy(
        source = UsageEventSource.USAGE_STATS,
        internalTransitionGrace = Duration.ofSeconds(3),
        boundaryLookback = Duration.ofHours(6)
    )

    @Test
    fun internalActivityTransitionsProduceOneOpeningAndOneDuration() = runTest {
        val completedAt = Instant.parse("2026-07-28T12:00:00Z")
        val eventRepository = FakeProjectionUsageEventRepository(
            listOf(
                event("2026-07-28T10:00:00Z", UsageEventType.FOREGROUND_ENTERED, "SplashActivity"),
                event("2026-07-28T10:00:01Z", UsageEventType.FOREGROUND_EXITED, "SplashActivity"),
                event("2026-07-28T10:00:01.100Z", UsageEventType.FOREGROUND_ENTERED, "MainActivity"),
                event("2026-07-28T10:10:00Z", UsageEventType.FOREGROUND_EXITED, "MainActivity")
            )
        )
        val dailyRepository = FakeDailyUsageRepository()
        val coordinator = coordinator(
            completedAt = completedAt,
            eventRepository = eventRepository,
            dailyRepository = dailyRepository
        )

        val projectionReport = coordinator.rebuild(reconciliationReport(completedAt))

        val usage = dailyRepository.replacements.getValue(LocalDate.parse("2026-07-28")).single()
        assertEquals(Duration.ofMinutes(10), usage.foregroundDuration)
        assertEquals(1, usage.estimatedOpeningCount)
        assertEquals(DataCompleteness.COMPLETE, usage.completeness)
        assertEquals(1, projectionReport.rebuiltDates.size)
    }

    @Test
    fun sessionCrossingMidnightIsSplitWithoutCountingASecondOpening() = runTest {
        val completedAt = Instant.parse("2026-07-29T00:10:00Z")
        val eventRepository = FakeProjectionUsageEventRepository(
            listOf(
                event("2026-07-28T23:55:00Z", UsageEventType.FOREGROUND_ENTERED, "MainActivity"),
                event("2026-07-29T00:05:00Z", UsageEventType.FOREGROUND_EXITED, "MainActivity")
            )
        )
        val dailyRepository = FakeDailyUsageRepository()
        val coordinator = coordinator(
            completedAt = completedAt,
            eventRepository = eventRepository,
            dailyRepository = dailyRepository
        )

        coordinator.rebuild(
            reconciliationReport(
                completedAt = completedAt,
                windowStart = Instant.parse("2026-07-28T23:00:00Z")
            )
        )

        val firstDay = dailyRepository.replacements.getValue(LocalDate.parse("2026-07-28")).single()
        val secondDay = dailyRepository.replacements.getValue(LocalDate.parse("2026-07-29")).single()
        assertEquals(Duration.ofMinutes(5), firstDay.foregroundDuration)
        assertEquals(1, firstDay.estimatedOpeningCount)
        assertEquals(Duration.ofMinutes(5), secondDay.foregroundDuration)
        assertEquals(0, secondDay.estimatedOpeningCount)
    }

    @Test
    fun overlappingCollectionGapMarksProjectionPartial() = runTest {
        val completedAt = Instant.parse("2026-07-28T12:00:00Z")
        val gap = CollectionGap(
            source = UsageEventSource.USAGE_STATS,
            startInclusive = Instant.parse("2026-07-28T08:00:00Z"),
            endExclusive = Instant.parse("2026-07-28T08:15:00Z"),
            reason = CollectionGapReason.DEVICE_LOCKED,
            detectedAt = completedAt
        )
        val dailyRepository = FakeDailyUsageRepository()
        val coordinator = coordinator(
            completedAt = completedAt,
            eventRepository = FakeProjectionUsageEventRepository(emptyList()),
            dailyRepository = dailyRepository,
            gaps = listOf(gap)
        )

        coordinator.rebuild(reconciliationReport(completedAt))

        assertEquals(
            DataCompleteness.PARTIAL,
            dailyRepository.replacements.getValue(LocalDate.parse("2026-07-28")).single().completeness
        )
    }

    private fun coordinator(
        completedAt: Instant,
        eventRepository: FakeProjectionUsageEventRepository,
        dailyRepository: FakeDailyUsageRepository,
        gaps: List<CollectionGap> = emptyList()
    ): DailyUsageProjectionCoordinator = DailyUsageProjectionCoordinator(
        monitoredApplicationRepository = FakeProjectionMonitoredApplicationRepository(
            listOf(
                MonitoredApplication(
                    packageName = packageName,
                    isEnabled = true,
                    addedAt = completedAt.minus(Duration.ofDays(2))
                )
            )
        ),
        usageEventRepository = eventRepository,
        observationStateRepository = FakeProjectionObservationStateRepository(
            checkpoint = CollectionCheckpoint(
                source = UsageEventSource.USAGE_STATS,
                reconciledThrough = completedAt,
                updatedAt = completedAt
            ),
            gaps = gaps
        ),
        dailyUsageRepository = dailyRepository,
        sessionReconstructor = UsageSessionReconstructor(policy),
        sessionPolicy = policy,
        timeZoneProvider = TimeZoneProvider { zoneId }
    )

    private fun reconciliationReport(
        completedAt: Instant,
        windowStart: Instant = completedAt.minus(Duration.ofHours(12))
    ): ObservationReconciliationReport = ObservationReconciliationReport(
        completedAt = completedAt,
        health = MonitoringHealth(
            usageAccessStatus = UsageAccessStatus.GRANTED,
            accessibilityStatus = AccessibilityMonitoringStatus.UNSUPPORTED,
            collectionStatus = CollectionStatus.HEALTHY,
            lastSuccessfulReconciliationAt = completedAt
        ),
        sourceOutcomes = listOf(
            SourceReconciliationOutcome(
                source = UsageEventSource.USAGE_STATS,
                window = ObservationWindow(windowStart, completedAt),
                status = SourceReconciliationStatus.COMPLETE,
                usageAccessStatus = UsageAccessStatus.GRANTED
            )
        ),
        healthPersisted = true,
        shouldRetry = false
    )

    private fun event(instant: String, type: UsageEventType, activityClassName: String): NormalizedUsageEvent {
        val occurredAt = Instant.parse(instant)
        return NormalizedUsageEvent(
            id = UsageEventId("${type.name}-${occurredAt.toEpochMilli()}-$activityClassName"),
            packageName = packageName,
            type = type,
            occurredAt = occurredAt,
            source = UsageEventSource.USAGE_STATS,
            reliability = UsageEventReliability.OBSERVED,
            activityClassName = activityClassName
        )
    }
}

private class FakeProjectionMonitoredApplicationRepository(private val applications: List<MonitoredApplication>) :
    MonitoredApplicationRepository {
    override fun observeAll(): Flow<List<MonitoredApplication>> = flowOf(applications)

    override suspend fun allApplications(): List<MonitoredApplication> = applications

    override suspend fun enabledApplications(): List<MonitoredApplication> =
        applications.filter(MonitoredApplication::isEnabled)

    override suspend fun save(application: MonitoredApplication) = Unit
}

private class FakeProjectionUsageEventRepository(private val events: List<NormalizedUsageEvent>) :
    UsageEventRepository {
    override suspend fun append(events: Collection<NormalizedUsageEvent>): UsageEventAppendResult =
        UsageEventAppendResult(events.size, 0)

    override suspend fun eventsIn(
        window: ObservationWindow,
        packageNames: Set<ApplicationPackageName>
    ): List<NormalizedUsageEvent> = events.filter { event ->
        event.packageName in packageNames && event.occurredAt in window
    }

    override suspend fun latestBefore(
        packageName: ApplicationPackageName,
        source: UsageEventSource,
        beforeExclusive: Instant
    ): NormalizedUsageEvent? = events
        .filter { event ->
            event.packageName == packageName &&
                event.source == source &&
                event.occurredAt < beforeExclusive
        }.maxByOrNull(NormalizedUsageEvent::occurredAt)
}

private class FakeProjectionObservationStateRepository(
    private val checkpoint: CollectionCheckpoint?,
    private val gaps: List<CollectionGap>
) : ObservationStateRepository {
    override fun observeHealth(): Flow<MonitoringHealth> = flowOf(DEFAULT_HEALTH)

    override suspend fun health(): MonitoringHealth = DEFAULT_HEALTH

    override suspend fun saveHealth(health: MonitoringHealth) = Unit

    override suspend fun checkpoint(source: UsageEventSource): CollectionCheckpoint? =
        checkpoint?.takeIf { item -> item.source == source }

    override suspend fun saveCheckpoint(checkpoint: CollectionCheckpoint) = Unit

    override fun observeGaps(window: ObservationWindow): Flow<List<CollectionGap>> = flowOf(gaps)

    override suspend fun gapsIn(window: ObservationWindow, source: UsageEventSource): List<CollectionGap> =
        gaps.filter { gap ->
            gap.source == source &&
                gap.startInclusive < window.endExclusive &&
                (gap.endExclusive == null || gap.endExclusive > window.startInclusive)
        }

    override suspend fun recordGap(gap: CollectionGap) = Unit

    private companion object {
        val DEFAULT_HEALTH = MonitoringHealth(
            usageAccessStatus = UsageAccessStatus.GRANTED,
            accessibilityStatus = AccessibilityMonitoringStatus.UNSUPPORTED,
            collectionStatus = CollectionStatus.HEALTHY,
            lastSuccessfulReconciliationAt = Instant.EPOCH
        )
    }
}

private class FakeDailyUsageRepository : DailyUsageRepository {
    val replacements = linkedMapOf<LocalDate, List<DailyApplicationUsage>>()

    override fun observe(date: LocalDate): Flow<List<DailyApplicationUsage>> = flowOf(replacements[date].orEmpty())

    override suspend fun replace(date: LocalDate, usage: List<DailyApplicationUsage>) {
        replacements[date] = usage
    }
}
