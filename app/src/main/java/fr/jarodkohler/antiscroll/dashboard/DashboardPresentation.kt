package fr.jarodkohler.antiscroll.dashboard

import androidx.compose.ui.graphics.ImageBitmap
import fr.jarodkohler.antiscroll.MonitoredApplicationUiModel
import fr.jarodkohler.antiscroll.R
import fr.jarodkohler.antiscroll.applicationconfig.SupportedApplicationCatalog
import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.observation.CollectionStatus
import fr.jarodkohler.antiscroll.domain.observation.DataCompleteness
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal data class DashboardApplicationRowUiModel(
    val packageName: ApplicationPackageName,
    val label: String,
    val icon: ImageBitmap?,
    val foregroundDuration: Duration,
    val estimatedOpeningCount: Int,
    val completeness: DataCompleteness
)

internal enum class MonitoringHealthTone {
    NEUTRAL,
    HEALTHY,
    DEGRADED,
    UNAVAILABLE
}

internal data class MonitoringHealthPresentation(
    val titleResource: Int,
    val descriptionResource: Int,
    val tone: MonitoringHealthTone
)

internal fun buildDashboardRows(
    uiState: DashboardUiState,
    applications: List<MonitoredApplicationUiModel>
): List<DashboardApplicationRowUiModel> {
    val installedByPackage = applications.associateBy(MonitoredApplicationUiModel::packageName)
    val fallbackLabels = SupportedApplicationCatalog.applications.associate { application ->
        application.packageName to application.fallbackLabel
    }
    val usageByPackage = uiState.dailyUsage.associateBy { usage -> usage.packageName }
    val packages = buildSet {
        addAll(
            uiState.dailyUsage
                .filter { usage ->
                    !usage.foregroundDuration.isZero || usage.estimatedOpeningCount > 0
                }.map { usage -> usage.packageName }
        )
        addAll(
            applications
                .filter(MonitoredApplicationUiModel::isEnabled)
                .map(MonitoredApplicationUiModel::packageName)
        )
    }

    return packages.map { packageName ->
        val installedApplication = installedByPackage[packageName]
        val usage = usageByPackage[packageName]
        DashboardApplicationRowUiModel(
            packageName = packageName,
            label = installedApplication?.label ?: fallbackLabels[packageName] ?: packageName.value,
            icon = installedApplication?.icon,
            foregroundDuration = usage?.foregroundDuration ?: Duration.ZERO,
            estimatedOpeningCount = usage?.estimatedOpeningCount ?: 0,
            completeness = usage?.completeness ?: DataCompleteness.UNKNOWN
        )
    }.sortedWith(
        compareByDescending<DashboardApplicationRowUiModel> { row -> row.foregroundDuration }
            .thenBy(DashboardApplicationRowUiModel::label)
    )
}

internal fun CollectionStatus.dashboardPresentation(): MonitoringHealthPresentation = when (this) {
    CollectionStatus.NOT_STARTED -> MonitoringHealthPresentation(
        titleResource = R.string.dashboard_health_not_started_title,
        descriptionResource = R.string.dashboard_health_not_started_body,
        tone = MonitoringHealthTone.NEUTRAL
    )

    CollectionStatus.HEALTHY -> MonitoringHealthPresentation(
        titleResource = R.string.dashboard_health_healthy_title,
        descriptionResource = R.string.dashboard_health_healthy_body,
        tone = MonitoringHealthTone.HEALTHY
    )

    CollectionStatus.DEGRADED -> MonitoringHealthPresentation(
        titleResource = R.string.dashboard_health_degraded_title,
        descriptionResource = R.string.dashboard_health_degraded_body,
        tone = MonitoringHealthTone.DEGRADED
    )

    CollectionStatus.UNAVAILABLE -> MonitoringHealthPresentation(
        titleResource = R.string.dashboard_health_unavailable_title,
        descriptionResource = R.string.dashboard_health_unavailable_body,
        tone = MonitoringHealthTone.UNAVAILABLE
    )
}

internal fun DataCompleteness.labelResource(): Int = when (this) {
    DataCompleteness.COMPLETE -> R.string.dashboard_completeness_complete
    DataCompleteness.PARTIAL -> R.string.dashboard_completeness_partial
    DataCompleteness.UNKNOWN -> R.string.dashboard_completeness_unknown
}

internal fun Duration.formatDashboardDuration(): String {
    val totalSeconds = seconds.coerceAtLeast(0)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val remainingSeconds = totalSeconds % 60

    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        minutes > 0 && remainingSeconds > 0 -> "${minutes}m ${remainingSeconds}s"
        minutes > 0 -> "${minutes}m"
        else -> "${remainingSeconds}s"
    }
}

internal fun LocalDate.formatForDashboard(): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))

internal fun Instant.formatTimeForDashboard(): String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    .withZone(ZoneId.systemDefault())
    .format(this)
