package fr.jarodkohler.antiscroll.monitoring.usagestats

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.observation.ObservationWindow

enum class UsageStatsActivityEventType {
    RESUMED,
    PAUSED
}

data class UsageStatsEventRecord(
    val packageName: String,
    val activityClassName: String?,
    val instanceId: Int,
    val eventType: UsageStatsActivityEventType,
    val occurredAtEpochMillis: Long
) {
    init {
        require(packageName.isNotBlank()) { "Usage stats package name must not be blank" }
        require(occurredAtEpochMillis >= 0) { "Usage stats timestamp must not be negative" }
    }
}

sealed interface UsageStatsQueryResult {
    data class Events(val records: List<UsageStatsEventRecord>) : UsageStatsQueryResult

    data object DeviceLocked : UsageStatsQueryResult

    data object UsageAccessMissing : UsageStatsQueryResult

    data object SourceUnavailable : UsageStatsQueryResult
}

interface UsageStatsEventGateway {
    suspend fun query(
        window: ObservationWindow,
        packageNames: Set<ApplicationPackageName>
    ): UsageStatsQueryResult
}

internal enum class UsageStatsQueryMode {
    FILTERED,
    LEGACY
}

internal fun usageStatsQueryModeForApi(sdkInt: Int): UsageStatsQueryMode =
    if (sdkInt >= 35) UsageStatsQueryMode.FILTERED else UsageStatsQueryMode.LEGACY
