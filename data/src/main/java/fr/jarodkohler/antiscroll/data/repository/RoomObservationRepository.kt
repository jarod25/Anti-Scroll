package fr.jarodkohler.antiscroll.data.repository

import androidx.room.withTransaction
import fr.jarodkohler.antiscroll.data.local.AntiScrollDatabase
import fr.jarodkohler.antiscroll.data.local.CollectionCheckpointEntity
import fr.jarodkohler.antiscroll.data.local.CollectionGapEntity
import fr.jarodkohler.antiscroll.data.local.MonitoringHealthEntity
import fr.jarodkohler.antiscroll.data.local.UsageEventEntity
import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.observation.AccessibilityMonitoringStatus
import fr.jarodkohler.antiscroll.domain.observation.CollectionCheckpoint
import fr.jarodkohler.antiscroll.domain.observation.CollectionGap
import fr.jarodkohler.antiscroll.domain.observation.CollectionGapReason
import fr.jarodkohler.antiscroll.domain.observation.CollectionStatus
import fr.jarodkohler.antiscroll.domain.observation.MonitoringHealth
import fr.jarodkohler.antiscroll.domain.observation.NormalizedUsageEvent
import fr.jarodkohler.antiscroll.domain.observation.ObservationCommitRepository
import fr.jarodkohler.antiscroll.domain.observation.ObservationStateRepository
import fr.jarodkohler.antiscroll.domain.observation.ObservationWindow
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus
import fr.jarodkohler.antiscroll.domain.observation.UsageEventAppendResult
import fr.jarodkohler.antiscroll.domain.observation.UsageEventId
import fr.jarodkohler.antiscroll.domain.observation.UsageEventReliability
import fr.jarodkohler.antiscroll.domain.observation.UsageEventRepository
import fr.jarodkohler.antiscroll.domain.observation.UsageEventSource
import fr.jarodkohler.antiscroll.domain.observation.UsageEventType
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomObservationRepository @Inject constructor(
    private val database: AntiScrollDatabase
) : UsageEventRepository, ObservationStateRepository, ObservationCommitRepository {
    private val dao = database.observationDao()

    override suspend fun append(events: Collection<NormalizedUsageEvent>): UsageEventAppendResult =
        appendEntities(events.map(NormalizedUsageEvent::toEntity))

    override suspend fun eventsIn(
        window: ObservationWindow,
        packageNames: Set<ApplicationPackageName>
    ): List<NormalizedUsageEvent> {
        if (packageNames.isEmpty()) return emptyList()

        return dao.findEventsIn(
            startInclusiveEpochMillis = window.startInclusive.toEpochMilli(),
            endExclusiveEpochMillis = window.endExclusive.toEpochMilli(),
            packageNames = packageNames.mapTo(mutableSetOf(), ApplicationPackageName::value)
        ).map(UsageEventEntity::toDomain)
    }

    override suspend fun appendAndCheckpoint(
        events: Collection<NormalizedUsageEvent>,
        checkpoint: CollectionCheckpoint
    ): UsageEventAppendResult {
        require(events.all { event -> event.source == checkpoint.source }) {
            "Committed events must match the checkpoint source"
        }

        return database.withTransaction {
            val result = appendEntities(events.map(NormalizedUsageEvent::toEntity))
            dao.upsertCheckpoint(checkpoint.toEntity())
            result
        }
    }

    override fun observeHealth(): Flow<MonitoringHealth> = dao.observeHealth().map { entity ->
        entity?.toDomain() ?: DEFAULT_MONITORING_HEALTH
    }

    override suspend fun saveHealth(health: MonitoringHealth) {
        dao.upsertHealth(health.toEntity())
    }

    override suspend fun checkpoint(source: UsageEventSource): CollectionCheckpoint? =
        dao.findCheckpoint(source.name)?.toDomain()

    override suspend fun saveCheckpoint(checkpoint: CollectionCheckpoint) {
        dao.upsertCheckpoint(checkpoint.toEntity())
    }

    override fun observeGaps(window: ObservationWindow): Flow<List<CollectionGap>> = dao.observeGaps(
        startInclusiveEpochMillis = window.startInclusive.toEpochMilli(),
        endExclusiveEpochMillis = window.endExclusive.toEpochMilli()
    ).map { entities -> entities.map(CollectionGapEntity::toDomain) }

    override suspend fun recordGap(gap: CollectionGap) {
        dao.insertGap(gap.toEntity())
    }

    private suspend fun appendEntities(events: List<UsageEventEntity>): UsageEventAppendResult {
        if (events.isEmpty()) return UsageEventAppendResult(insertedCount = 0, duplicateCount = 0)

        val rowIds = dao.insertEvents(events)
        val insertedCount = rowIds.count { rowId -> rowId != IGNORED_ROW_ID }
        return UsageEventAppendResult(
            insertedCount = insertedCount,
            duplicateCount = events.size - insertedCount
        )
    }

    private companion object {
        const val IGNORED_ROW_ID = -1L

        val DEFAULT_MONITORING_HEALTH = MonitoringHealth(
            usageAccessStatus = UsageAccessStatus.UNAVAILABLE,
            accessibilityStatus = AccessibilityMonitoringStatus.UNSUPPORTED,
            collectionStatus = CollectionStatus.NOT_STARTED,
            lastSuccessfulReconciliationAt = null
        )
    }
}

private fun NormalizedUsageEvent.toEntity(): UsageEventEntity = UsageEventEntity(
    eventId = id.value,
    packageName = packageName.value,
    eventType = type.name,
    occurredAtEpochMillis = occurredAt.toEpochMilli(),
    source = source.name,
    reliability = reliability.name
)

private fun UsageEventEntity.toDomain(): NormalizedUsageEvent = NormalizedUsageEvent(
    id = UsageEventId(eventId),
    packageName = ApplicationPackageName(packageName),
    type = UsageEventType.valueOf(eventType),
    occurredAt = Instant.ofEpochMilli(occurredAtEpochMillis),
    source = UsageEventSource.valueOf(source),
    reliability = UsageEventReliability.valueOf(reliability)
)

private fun CollectionCheckpoint.toEntity(): CollectionCheckpointEntity = CollectionCheckpointEntity(
    source = source.name,
    reconciledThroughEpochMillis = reconciledThrough.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli()
)

private fun CollectionCheckpointEntity.toDomain(): CollectionCheckpoint = CollectionCheckpoint(
    source = UsageEventSource.valueOf(source),
    reconciledThrough = Instant.ofEpochMilli(reconciledThroughEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis)
)

private fun CollectionGap.toEntity(): CollectionGapEntity = CollectionGapEntity(
    source = source.name,
    startInclusiveEpochMillis = startInclusive.toEpochMilli(),
    endExclusiveEpochMillis = endExclusive?.toEpochMilli(),
    reason = reason.name,
    detectedAtEpochMillis = detectedAt.toEpochMilli()
)

private fun CollectionGapEntity.toDomain(): CollectionGap = CollectionGap(
    source = UsageEventSource.valueOf(source),
    startInclusive = Instant.ofEpochMilli(startInclusiveEpochMillis),
    endExclusive = endExclusiveEpochMillis?.let(Instant::ofEpochMilli),
    reason = CollectionGapReason.valueOf(reason),
    detectedAt = Instant.ofEpochMilli(detectedAtEpochMillis)
)

private fun MonitoringHealth.toEntity(): MonitoringHealthEntity = MonitoringHealthEntity(
    singletonId = MonitoringHealthEntity.SINGLETON_ID,
    usageAccessStatus = usageAccessStatus.name,
    accessibilityStatus = accessibilityStatus.name,
    collectionStatus = collectionStatus.name,
    lastSuccessfulReconciliationEpochMillis = lastSuccessfulReconciliationAt?.toEpochMilli()
)

private fun MonitoringHealthEntity.toDomain(): MonitoringHealth = MonitoringHealth(
    usageAccessStatus = UsageAccessStatus.valueOf(usageAccessStatus),
    accessibilityStatus = AccessibilityMonitoringStatus.valueOf(accessibilityStatus),
    collectionStatus = CollectionStatus.valueOf(collectionStatus),
    lastSuccessfulReconciliationAt = lastSuccessfulReconciliationEpochMillis?.let(Instant::ofEpochMilli)
)
