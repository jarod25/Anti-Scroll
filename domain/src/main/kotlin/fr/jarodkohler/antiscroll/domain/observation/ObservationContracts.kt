package fr.jarodkohler.antiscroll.domain.observation

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.application.MonitoredApplication
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface MonitoredApplicationRepository {
    fun observeAll(): Flow<List<MonitoredApplication>>

    suspend fun allApplications(): List<MonitoredApplication>

    suspend fun enabledApplications(): List<MonitoredApplication>

    suspend fun save(application: MonitoredApplication)
}

data class UsageEventAppendResult(val insertedCount: Int, val duplicateCount: Int) {
    init {
        require(insertedCount >= 0) { "Inserted event count must not be negative" }
        require(duplicateCount >= 0) { "Duplicate event count must not be negative" }
    }
}

interface UsageEventRepository {
    suspend fun append(events: Collection<NormalizedUsageEvent>): UsageEventAppendResult

    suspend fun eventsIn(
        window: ObservationWindow,
        packageNames: Set<ApplicationPackageName>
    ): List<NormalizedUsageEvent>

    suspend fun latestBefore(
        packageName: ApplicationPackageName,
        source: UsageEventSource,
        beforeExclusive: Instant
    ): NormalizedUsageEvent?
}

/** Commits one reconciled source window without advancing its checkpoint independently. */
interface ObservationCommitRepository {
    suspend fun appendAndCheckpoint(
        events: Collection<NormalizedUsageEvent>,
        checkpoint: CollectionCheckpoint
    ): UsageEventAppendResult
}

interface DailyUsageRepository {
    fun observe(date: LocalDate): Flow<List<DailyApplicationUsage>>

    suspend fun replace(date: LocalDate, usage: List<DailyApplicationUsage>)
}

interface ObservationStateRepository {
    fun observeHealth(): Flow<MonitoringHealth>

    suspend fun health(): MonitoringHealth

    suspend fun saveHealth(health: MonitoringHealth)

    suspend fun checkpoint(source: UsageEventSource): CollectionCheckpoint?

    suspend fun saveCheckpoint(checkpoint: CollectionCheckpoint)

    fun observeGaps(window: ObservationWindow): Flow<List<CollectionGap>>

    suspend fun gapsIn(window: ObservationWindow, source: UsageEventSource): List<CollectionGap>

    suspend fun recordGap(gap: CollectionGap)
}

interface UsageObservationSource {
    val source: UsageEventSource

    suspend fun collect(window: ObservationWindow, packageNames: Set<ApplicationPackageName>): UsageCollectionResult
}
