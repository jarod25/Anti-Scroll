package fr.jarodkohler.antiscroll.engine.observation

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.observation.AccessibilityMonitoringStatus
import fr.jarodkohler.antiscroll.domain.observation.CollectionCheckpoint
import fr.jarodkohler.antiscroll.domain.observation.CollectionGap
import fr.jarodkohler.antiscroll.domain.observation.CollectionGapReason
import fr.jarodkohler.antiscroll.domain.observation.CollectionStatus
import fr.jarodkohler.antiscroll.domain.observation.DataCompleteness
import fr.jarodkohler.antiscroll.domain.observation.MonitoredApplicationRepository
import fr.jarodkohler.antiscroll.domain.observation.MonitoringHealth
import fr.jarodkohler.antiscroll.domain.observation.ObservationCommitRepository
import fr.jarodkohler.antiscroll.domain.observation.ObservationStateRepository
import fr.jarodkohler.antiscroll.domain.observation.ObservationWindow
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus
import fr.jarodkohler.antiscroll.domain.observation.UsageCollectionResult
import fr.jarodkohler.antiscroll.domain.observation.UsageEventRepository
import fr.jarodkohler.antiscroll.domain.observation.UsageEventSource
import fr.jarodkohler.antiscroll.domain.observation.UsageObservationSource
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SourceReconciliationStatus {
    COMPLETE,
    PARTIAL,
    UNAVAILABLE,
    PERSISTENCE_FAILED
}

data class SourceReconciliationOutcome(
    val source: UsageEventSource,
    val window: ObservationWindow,
    val status: SourceReconciliationStatus,
    val insertedEventCount: Int = 0,
    val duplicateEventCount: Int = 0,
    val failureReason: CollectionGapReason? = null,
    val usageAccessStatus: UsageAccessStatus? = null,
    val diagnosticsPersisted: Boolean = true,
    val shouldRetry: Boolean = false
) {
    init {
        require(insertedEventCount >= 0) { "Inserted event count must not be negative" }
        require(duplicateEventCount >= 0) { "Duplicate event count must not be negative" }
        require(status == SourceReconciliationStatus.COMPLETE || failureReason != null) {
            "Non-complete reconciliation outcomes require a failure reason"
        }
    }
}

data class ObservationReconciliationReport(
    val completedAt: Instant,
    val health: MonitoringHealth,
    val sourceOutcomes: List<SourceReconciliationOutcome>,
    val healthPersisted: Boolean,
    val shouldRetry: Boolean
) {
    val insertedEventCount: Int = sourceOutcomes.sumOf(SourceReconciliationOutcome::insertedEventCount)
    val duplicateEventCount: Int = sourceOutcomes.sumOf(SourceReconciliationOutcome::duplicateEventCount)
}

class ObservationReconciliationCoordinator(
    sources: Set<UsageObservationSource>,
    private val monitoredApplicationRepository: MonitoredApplicationRepository,
    private val usageEventRepository: UsageEventRepository,
    private val observationCommitRepository: ObservationCommitRepository,
    private val observationStateRepository: ObservationStateRepository,
    private val clock: Clock,
    private val policy: ObservationReconciliationPolicy
) {
    private val sources = sources.sortedBy { source -> source.source.name }
    private val reconciliationMutex = Mutex()

    init {
        require(this.sources.isNotEmpty()) { "At least one usage observation source is required" }
        require(this.sources.map(UsageObservationSource::source).distinct().size == this.sources.size) {
            "Usage observation sources must have unique source identifiers"
        }
    }

    suspend fun reconcile(): ObservationReconciliationReport = reconciliationMutex.withLock {
        reconcileLocked()
    }

    private suspend fun reconcileLocked(): ObservationReconciliationReport {
        val now = clock.instant()
        val previousHealth = runCatching { observationStateRepository.health() }.getOrElse {
            return persistenceUnavailableReport(now)
        }
        val packageNames = runCatching {
            monitoredApplicationRepository.enabledApplications()
                .mapTo(linkedSetOf()) { application -> application.packageName }
        }.getOrElse {
            return degradedReport(now, previousHealth)
        }

        val outcomes = sources.map { source ->
            reconcileSource(source, packageNames, now)
        }
        val usageAccessStatus = outcomes
            .firstOrNull { outcome -> outcome.source == UsageEventSource.USAGE_STATS }
            ?.usageAccessStatus
            ?: previousHealth.usageAccessStatus
        val allSourcesComplete = outcomes.all { outcome ->
            outcome.status == SourceReconciliationStatus.COMPLETE
        }
        val collectionStatus = when {
            usageAccessStatus != UsageAccessStatus.GRANTED -> CollectionStatus.UNAVAILABLE
            allSourcesComplete -> CollectionStatus.HEALTHY
            else -> CollectionStatus.DEGRADED
        }
        val health = MonitoringHealth(
            usageAccessStatus = usageAccessStatus,
            accessibilityStatus = previousHealth.accessibilityStatus,
            collectionStatus = collectionStatus,
            lastSuccessfulReconciliationAt = if (allSourcesComplete) {
                now
            } else {
                previousHealth.lastSuccessfulReconciliationAt
            }
        )
        val healthPersisted = runCatching {
            observationStateRepository.saveHealth(health)
        }.isSuccess

        return ObservationReconciliationReport(
            completedAt = now,
            health = health,
            sourceOutcomes = outcomes,
            healthPersisted = healthPersisted,
            shouldRetry = !healthPersisted || outcomes.any(SourceReconciliationOutcome::shouldRetry)
        )
    }

    private suspend fun reconcileSource(
        source: UsageObservationSource,
        packageNames: Set<ApplicationPackageName>,
        now: Instant
    ): SourceReconciliationOutcome {
        val checkpoint = runCatching {
            observationStateRepository.checkpoint(source.source)
        }.getOrElse {
            val window = initialWindow(now)
            persistGap(source.source, window, CollectionGapReason.PERSISTENCE_FAILURE, now)
            return SourceReconciliationOutcome(
                source = source.source,
                window = window,
                status = SourceReconciliationStatus.PERSISTENCE_FAILED,
                failureReason = CollectionGapReason.PERSISTENCE_FAILURE,
                diagnosticsPersisted = false,
                shouldRetry = true
            )
        }
        val window = windowFor(checkpoint, now)
        val result = runCatching {
            source.collect(window, packageNames)
        }.getOrElse {
            val persisted = persistGap(source.source, window, CollectionGapReason.UNKNOWN, now)
            return SourceReconciliationOutcome(
                source = source.source,
                window = window,
                status = SourceReconciliationStatus.UNAVAILABLE,
                failureReason = CollectionGapReason.UNKNOWN,
                usageAccessStatus = source.errorUsageAccessStatus(),
                diagnosticsPersisted = persisted,
                shouldRetry = true
            )
        }

        if (result.source != source.source || result.window != window) {
            val persisted = persistGap(source.source, window, CollectionGapReason.UNKNOWN, now)
            return SourceReconciliationOutcome(
                source = source.source,
                window = window,
                status = SourceReconciliationStatus.UNAVAILABLE,
                failureReason = CollectionGapReason.UNKNOWN,
                usageAccessStatus = source.errorUsageAccessStatus(),
                diagnosticsPersisted = persisted,
                shouldRetry = true
            )
        }

        return when (result) {
            is UsageCollectionResult.Collected -> reconcileCollected(result, now)
            is UsageCollectionResult.Unavailable -> reconcileUnavailable(result, now)
        }
    }

    private suspend fun reconcileCollected(
        result: UsageCollectionResult.Collected,
        now: Instant
    ): SourceReconciliationOutcome = when (result.completeness) {
        DataCompleteness.COMPLETE -> {
            val appendResult = runCatching {
                observationCommitRepository.appendAndCheckpoint(
                    events = result.events,
                    checkpoint = CollectionCheckpoint(
                        source = result.source,
                        reconciledThrough = result.window.endExclusive,
                        updatedAt = now
                    )
                )
            }.getOrElse {
                persistGap(result.source, result.window, CollectionGapReason.PERSISTENCE_FAILURE, now)
                return SourceReconciliationOutcome(
                    source = result.source,
                    window = result.window,
                    status = SourceReconciliationStatus.PERSISTENCE_FAILED,
                    failureReason = CollectionGapReason.PERSISTENCE_FAILURE,
                    usageAccessStatus = result.source.collectedUsageAccessStatus(),
                    diagnosticsPersisted = false,
                    shouldRetry = true
                )
            }

            SourceReconciliationOutcome(
                source = result.source,
                window = result.window,
                status = SourceReconciliationStatus.COMPLETE,
                insertedEventCount = appendResult.insertedCount,
                duplicateEventCount = appendResult.duplicateCount,
                usageAccessStatus = result.source.collectedUsageAccessStatus()
            )
        }

        DataCompleteness.PARTIAL -> {
            val reason = requireNotNull(result.incompleteReason)
            val appendResult = runCatching {
                usageEventRepository.append(result.events)
            }.getOrElse {
                persistGap(result.source, result.window, CollectionGapReason.PERSISTENCE_FAILURE, now)
                return SourceReconciliationOutcome(
                    source = result.source,
                    window = result.window,
                    status = SourceReconciliationStatus.PERSISTENCE_FAILED,
                    failureReason = CollectionGapReason.PERSISTENCE_FAILURE,
                    usageAccessStatus = result.source.collectedUsageAccessStatus(),
                    diagnosticsPersisted = false,
                    shouldRetry = true
                )
            }
            val gapPersisted = persistGap(result.source, result.window, reason, now)

            SourceReconciliationOutcome(
                source = result.source,
                window = result.window,
                status = SourceReconciliationStatus.PARTIAL,
                insertedEventCount = appendResult.insertedCount,
                duplicateEventCount = appendResult.duplicateCount,
                failureReason = reason,
                usageAccessStatus = result.source.collectedUsageAccessStatus(),
                diagnosticsPersisted = gapPersisted,
                shouldRetry = !gapPersisted || reason.isRetryable()
            )
        }

        DataCompleteness.UNKNOWN -> error("Collected usage cannot have unknown completeness")
    }

    private suspend fun reconcileUnavailable(
        result: UsageCollectionResult.Unavailable,
        now: Instant
    ): SourceReconciliationOutcome {
        val gapPersisted = persistGap(result.source, result.window, result.reason, now)
        return SourceReconciliationOutcome(
            source = result.source,
            window = result.window,
            status = SourceReconciliationStatus.UNAVAILABLE,
            failureReason = result.reason,
            usageAccessStatus = result.source.unavailableUsageAccessStatus(result.reason),
            diagnosticsPersisted = gapPersisted,
            shouldRetry = !gapPersisted || result.reason.isRetryable()
        )
    }

    private suspend fun persistGap(
        source: UsageEventSource,
        window: ObservationWindow,
        reason: CollectionGapReason,
        detectedAt: Instant
    ): Boolean = runCatching {
        observationStateRepository.recordGap(
            CollectionGap(
                source = source,
                startInclusive = window.startInclusive,
                endExclusive = window.endExclusive,
                reason = reason,
                detectedAt = detectedAt
            )
        )
    }.isSuccess

    private fun windowFor(checkpoint: CollectionCheckpoint?, now: Instant): ObservationWindow {
        val earliestAllowed = now.minus(policy.maximumLookback)
        val requestedStart = checkpoint?.reconciledThrough
            ?.minus(policy.replayOverlap)
            ?: now.minus(policy.initialLookback)
        val boundedStart = if (requestedStart.isBefore(earliestAllowed)) {
            earliestAllowed
        } else {
            requestedStart
        }
        val safeStart = if (boundedStart.isBefore(now)) boundedStart else now.minusMillis(1)
        return ObservationWindow(startInclusive = safeStart, endExclusive = now)
    }

    private fun initialWindow(now: Instant): ObservationWindow = ObservationWindow(
        startInclusive = now.minus(policy.initialLookback),
        endExclusive = now
    )

    private suspend fun persistenceUnavailableReport(now: Instant): ObservationReconciliationReport {
        val health = DEFAULT_HEALTH.copy(collectionStatus = CollectionStatus.DEGRADED)
        return ObservationReconciliationReport(
            completedAt = now,
            health = health,
            sourceOutcomes = emptyList(),
            healthPersisted = false,
            shouldRetry = true
        )
    }

    private suspend fun degradedReport(
        now: Instant,
        previousHealth: MonitoringHealth
    ): ObservationReconciliationReport {
        val health = previousHealth.copy(
            collectionStatus = if (previousHealth.usageAccessStatus == UsageAccessStatus.GRANTED) {
                CollectionStatus.DEGRADED
            } else {
                CollectionStatus.UNAVAILABLE
            }
        )
        val healthPersisted = runCatching {
            observationStateRepository.saveHealth(health)
        }.isSuccess
        return ObservationReconciliationReport(
            completedAt = now,
            health = health,
            sourceOutcomes = emptyList(),
            healthPersisted = healthPersisted,
            shouldRetry = true
        )
    }

    private companion object {
        val DEFAULT_HEALTH = MonitoringHealth(
            usageAccessStatus = UsageAccessStatus.UNAVAILABLE,
            accessibilityStatus = AccessibilityMonitoringStatus.UNSUPPORTED,
            collectionStatus = CollectionStatus.NOT_STARTED,
            lastSuccessfulReconciliationAt = null
        )
    }
}

private fun CollectionGapReason.isRetryable(): Boolean = when (this) {
    CollectionGapReason.DEVICE_LOCKED,
    CollectionGapReason.SOURCE_UNAVAILABLE,
    CollectionGapReason.PERSISTENCE_FAILURE,
    CollectionGapReason.UNKNOWN -> true

    CollectionGapReason.USAGE_ACCESS_MISSING,
    CollectionGapReason.HISTORY_INCOMPLETE -> false
}

private fun UsageEventSource.collectedUsageAccessStatus(): UsageAccessStatus? = when (this) {
    UsageEventSource.USAGE_STATS -> UsageAccessStatus.GRANTED
    UsageEventSource.ACCESSIBILITY -> null
}

private fun UsageEventSource.errorUsageAccessStatus(): UsageAccessStatus? = when (this) {
    UsageEventSource.USAGE_STATS -> UsageAccessStatus.ERROR
    UsageEventSource.ACCESSIBILITY -> null
}

private fun UsageEventSource.unavailableUsageAccessStatus(reason: CollectionGapReason): UsageAccessStatus? = when (this) {
    UsageEventSource.ACCESSIBILITY -> null
    UsageEventSource.USAGE_STATS -> when (reason) {
        CollectionGapReason.USAGE_ACCESS_MISSING -> UsageAccessStatus.MISSING
        CollectionGapReason.DEVICE_LOCKED,
        CollectionGapReason.HISTORY_INCOMPLETE,
        CollectionGapReason.PERSISTENCE_FAILURE -> UsageAccessStatus.GRANTED

        CollectionGapReason.SOURCE_UNAVAILABLE -> UsageAccessStatus.UNAVAILABLE
        CollectionGapReason.UNKNOWN -> UsageAccessStatus.ERROR
    }
}
