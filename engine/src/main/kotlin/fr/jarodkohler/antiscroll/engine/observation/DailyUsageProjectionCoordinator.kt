package fr.jarodkohler.antiscroll.engine.observation

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.observation.DailyApplicationUsage
import fr.jarodkohler.antiscroll.domain.observation.DailyUsageRepository
import fr.jarodkohler.antiscroll.domain.observation.DataCompleteness
import fr.jarodkohler.antiscroll.domain.observation.MonitoredApplicationRepository
import fr.jarodkohler.antiscroll.domain.observation.ObservationStateRepository
import fr.jarodkohler.antiscroll.domain.observation.ObservationWindow
import fr.jarodkohler.antiscroll.domain.observation.UsageEventRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Rebuilds replaceable daily statistics from the durable normalized event journal. */
class DailyUsageProjectionCoordinator(
    private val monitoredApplicationRepository: MonitoredApplicationRepository,
    private val usageEventRepository: UsageEventRepository,
    private val observationStateRepository: ObservationStateRepository,
    private val dailyUsageRepository: DailyUsageRepository,
    private val sessionReconstructor: UsageSessionReconstructor,
    private val sessionPolicy: UsageSessionReconstructionPolicy,
    private val zoneId: ZoneId
) {
    suspend fun rebuild(report: ObservationReconciliationReport): DailyUsageProjectionReport {
        val dates = report.sourceOutcomes.asSequence()
            .filter { outcome -> outcome.source == sessionPolicy.source }
            .flatMap { outcome -> outcome.window.datesThrough(report.completedAt, zoneId).asSequence() }
            .toSortedSet()

        var projectedRowCount = 0
        dates.forEach { date ->
            projectedRowCount += rebuild(date, report.completedAt)
        }

        return DailyUsageProjectionReport(
            rebuiltDates = dates,
            projectedRowCount = projectedRowCount
        )
    }

    private suspend fun rebuild(date: LocalDate, projectionThrough: Instant): Int {
        val dayStart = date.atStartOfDay(zoneId).toInstant()
        if (!dayStart.isBefore(projectionThrough)) return 0

        val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant()
        val targetEnd = minOf(dayEnd, projectionThrough)
        val targetWindow = ObservationWindow(dayStart, targetEnd)
        val contextStart = dayStart.minus(sessionPolicy.boundaryLookback)
        val contextEnd = minOf(dayEnd.plus(sessionPolicy.internalTransitionGrace), projectionThrough)
        val contextWindow = ObservationWindow(contextStart, contextEnd)

        val applications = monitoredApplicationRepository.allApplications()
        val packageNames = applications.mapTo(linkedSetOf()) { application -> application.packageName }
        if (packageNames.isEmpty()) {
            dailyUsageRepository.replace(date, emptyList())
            return 0
        }

        val sourceEvents = usageEventRepository.eventsIn(contextWindow, packageNames)
            .filter { event -> event.source == sessionPolicy.source }
            .groupBy { event -> event.packageName }
        val completeness = determineCompleteness(targetWindow)

        val dailyUsage = packageNames.sortedBy(ApplicationPackageName::value).map { packageName ->
            val eventBeforeWindow = usageEventRepository.latestBefore(
                packageName = packageName,
                source = sessionPolicy.source,
                beforeExclusive = contextWindow.startInclusive
            )
            val sessions = sessionReconstructor.reconstruct(
                packageName = packageName,
                window = contextWindow,
                events = sourceEvents[packageName].orEmpty(),
                eventBeforeWindow = eventBeforeWindow
            )

            DailyApplicationUsage(
                date = date,
                packageName = packageName,
                foregroundDuration = sessions.sumOverlap(targetWindow),
                estimatedOpeningCount = sessions.count { session ->
                    !session.startInferred && session.startInclusive in targetWindow
                },
                completeness = completeness
            )
        }

        dailyUsageRepository.replace(date, dailyUsage)
        return dailyUsage.size
    }

    private suspend fun determineCompleteness(window: ObservationWindow): DataCompleteness {
        val checkpoint = observationStateRepository.checkpoint(sessionPolicy.source)
            ?: return DataCompleteness.UNKNOWN
        if (checkpoint.reconciledThrough <= window.startInclusive) return DataCompleteness.UNKNOWN
        if (checkpoint.reconciledThrough < window.endExclusive) return DataCompleteness.PARTIAL

        val gaps = observationStateRepository.gapsIn(window, sessionPolicy.source)
        return if (gaps.isEmpty()) DataCompleteness.COMPLETE else DataCompleteness.PARTIAL
    }
}

data class DailyUsageProjectionReport(
    val rebuiltDates: Set<LocalDate>,
    val projectedRowCount: Int
) {
    init {
        require(projectedRowCount >= 0) { "Projected usage row count must not be negative" }
    }
}

private fun ObservationWindow.datesThrough(through: Instant, zoneId: ZoneId): Set<LocalDate> {
    val effectiveEnd = minOf(endExclusive, through)
    if (!startInclusive.isBefore(effectiveEnd)) return emptySet()

    val firstDate = startInclusive.atZone(zoneId).toLocalDate()
    val lastDate = effectiveEnd.minusNanos(1).atZone(zoneId).toLocalDate()
    return buildSet {
        var date = firstDate
        while (!date.isAfter(lastDate)) {
            add(date)
            date = date.plusDays(1)
        }
    }
}

private fun Collection<ReconstructedUsageSession>.sumOverlap(window: ObservationWindow): Duration = fold(
    Duration.ZERO
) { total, session ->
    val overlapStart = maxOf(session.startInclusive, window.startInclusive)
    val overlapEnd = minOf(session.endExclusive, window.endExclusive)
    if (overlapStart < overlapEnd) {
        total.plus(Duration.between(overlapStart, overlapEnd))
    } else {
        total
    }
}
