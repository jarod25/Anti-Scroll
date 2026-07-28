package fr.jarodkohler.antiscroll.monitoring.usagestats

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.observation.CollectionGapReason
import fr.jarodkohler.antiscroll.domain.observation.DataCompleteness
import fr.jarodkohler.antiscroll.domain.observation.NormalizedUsageEvent
import fr.jarodkohler.antiscroll.domain.observation.ObservationWindow
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus
import fr.jarodkohler.antiscroll.domain.observation.UsageCollectionResult
import fr.jarodkohler.antiscroll.domain.observation.UsageEventSource
import fr.jarodkohler.antiscroll.domain.observation.UsageObservationSource
import fr.jarodkohler.antiscroll.monitoring.permission.MonitoringPermissionReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageStatsObservationSource
@Inject
constructor(
    private val permissionReader: MonitoringPermissionReader,
    private val eventGateway: UsageStatsEventGateway,
    private val normalizer: UsageStatsEventNormalizer
) : UsageObservationSource {
    override val source: UsageEventSource = UsageEventSource.USAGE_STATS

    override suspend fun collect(
        window: ObservationWindow,
        packageNames: Set<ApplicationPackageName>
    ): UsageCollectionResult {
        if (packageNames.isEmpty()) {
            return collected(window, emptyList())
        }

        when (permissionReader.read().usageAccessStatus) {
            UsageAccessStatus.MISSING ->
                return unavailable(
                    window,
                    CollectionGapReason.USAGE_ACCESS_MISSING
                )

            UsageAccessStatus.UNAVAILABLE,
            UsageAccessStatus.ERROR ->
                return unavailable(
                    window,
                    CollectionGapReason.SOURCE_UNAVAILABLE
                )

            UsageAccessStatus.GRANTED -> Unit
        }

        return when (val queryResult = eventGateway.query(window, packageNames)) {
            is UsageStatsQueryResult.Events ->
                collected(
                    window = window,
                    events = normalizer.normalize(queryResult.records, window, packageNames)
                )

            UsageStatsQueryResult.DeviceLocked ->
                unavailable(
                    window,
                    CollectionGapReason.DEVICE_LOCKED
                )

            UsageStatsQueryResult.UsageAccessMissing ->
                unavailable(
                    window,
                    CollectionGapReason.USAGE_ACCESS_MISSING
                )

            UsageStatsQueryResult.SourceUnavailable ->
                unavailable(
                    window,
                    CollectionGapReason.SOURCE_UNAVAILABLE
                )
        }
    }

    private fun collected(
        window: ObservationWindow,
        events: List<NormalizedUsageEvent>
    ): UsageCollectionResult.Collected = UsageCollectionResult.Collected(
        source = source,
        window = window,
        events = events,
        completeness = DataCompleteness.COMPLETE
    )

    private fun unavailable(window: ObservationWindow, reason: CollectionGapReason): UsageCollectionResult.Unavailable =
        UsageCollectionResult.Unavailable(
            source = source,
            window = window,
            reason = reason
        )
}
