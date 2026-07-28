package fr.jarodkohler.antiscroll.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.jarodkohler.antiscroll.MonitoredApplicationUiModel
import fr.jarodkohler.antiscroll.R
import fr.jarodkohler.antiscroll.applicationconfig.SupportedApplicationCatalog
import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.observation.CollectionStatus
import fr.jarodkohler.antiscroll.domain.observation.DataCompleteness
import fr.jarodkohler.antiscroll.ui.components.ApplicationIcon
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object DashboardTestTags {
    const val SUMMARY = "dashboard_summary"
    const val HEALTH = "dashboard_health"
    const val EMPTY = "dashboard_empty"
    const val APP_LIST = "dashboard_app_list"
    const val REFRESH = "dashboard_refresh"

    fun application(packageName: ApplicationPackageName): String = "dashboard_application_${packageName.value}"
}

private data class DashboardApplicationRowUiModel(
    val packageName: ApplicationPackageName,
    val label: String,
    val icon: androidx.compose.ui.graphics.ImageBitmap?,
    val foregroundDuration: Duration,
    val estimatedOpeningCount: Int,
    val completeness: DataCompleteness
)

@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    applications: List<MonitoredApplicationUiModel>,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = remember(uiState.dailyUsage, applications) {
        buildDashboardRows(uiState, applications)
    }
    val totalDuration = remember(uiState.dailyUsage) {
        uiState.dailyUsage.fold(Duration.ZERO) { total, usage -> total.plus(usage.foregroundDuration) }
    }
    val totalOpenings = remember(uiState.dailyUsage) {
        uiState.dailyUsage.sumOf { usage -> usage.estimatedOpeningCount }
    }
    val maximumDurationMillis = rows.maxOfOrNull { row -> row.foregroundDuration.toMillis() } ?: 0L

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            DashboardHeader(
                dateLabel = uiState.date.formatForDashboard(),
                onRefresh = onRefresh
            )
        }

        item {
            DailySummaryCard(
                totalDuration = totalDuration,
                totalOpenings = totalOpenings
            )
        }

        item {
            MonitoringHealthCard(
                collectionStatus = uiState.monitoringHealth.collectionStatus,
                lastSuccessfulReconciliationAt = uiState.monitoringHealth.lastSuccessfulReconciliationAt
            )
        }

        if (uiState.loadFailed) {
            item {
                Text(
                    text = stringResource(R.string.dashboard_load_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.dashboard_applications_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        if (rows.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.dashboard_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp)
                        .testTag(DashboardTestTags.EMPTY),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(
                items = rows,
                key = { row -> row.packageName.value }
            ) { row ->
                DashboardApplicationRow(
                    row = row,
                    maximumDurationMillis = maximumDurationMillis,
                    modifier = Modifier.testTag(DashboardTestTags.application(row.packageName))
                )
                HorizontalDivider()
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DashboardHeader(dateLabel: String, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.dashboard_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(
            onClick = onRefresh,
            modifier = Modifier.testTag(DashboardTestTags.REFRESH)
        ) {
            Text(text = stringResource(R.string.dashboard_refresh))
        }
    }
}

@Composable
private fun DailySummaryCard(totalDuration: Duration, totalOpenings: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DashboardTestTags.SUMMARY)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = totalDuration.formatDashboardDuration(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.dashboard_total_time),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = totalOpenings.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(
                        if (totalOpenings == 1) {
                            R.string.dashboard_opening
                        } else {
                            R.string.dashboard_openings
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MonitoringHealthCard(
    collectionStatus: CollectionStatus,
    lastSuccessfulReconciliationAt: Instant?
) {
    val presentation = monitoringHealthPresentation(collectionStatus)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DashboardTestTags.HEALTH),
        shape = MaterialTheme.shapes.medium,
        color = presentation.containerColor()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(presentation.titleResource),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(presentation.descriptionResource),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            lastSuccessfulReconciliationAt?.let { instant ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.dashboard_last_updated,
                        instant.formatTimeForDashboard()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DashboardApplicationRow(
    row: DashboardApplicationRowUiModel,
    maximumDurationMillis: Long,
    modifier: Modifier = Modifier
) {
    val progress = if (maximumDurationMillis <= 0L) {
        0f
    } else {
        (row.foregroundDuration.toMillis().toFloat() / maximumDurationMillis).coerceIn(0f, 1f)
    }

    ListItem(
        modifier = modifier,
        leadingContent = {
            ApplicationIcon(label = row.label, icon = row.icon)
        },
        headlineContent = {
            Text(text = row.label, fontWeight = FontWeight.SemiBold)
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(
                        R.string.dashboard_application_summary,
                        row.foregroundDuration.formatDashboardDuration(),
                        row.estimatedOpeningCount
                    )
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        trailingContent = {
            Text(
                text = stringResource(row.completeness.labelResource()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

private fun buildDashboardRows(
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

private data class MonitoringHealthPresentation(
    val titleResource: Int,
    val descriptionResource: Int,
    val containerColor: @Composable () -> Color
)

@Composable
private fun monitoringHealthPresentation(status: CollectionStatus): MonitoringHealthPresentation = when (status) {
    CollectionStatus.NOT_STARTED -> MonitoringHealthPresentation(
        titleResource = R.string.dashboard_health_not_started_title,
        descriptionResource = R.string.dashboard_health_not_started_body,
        containerColor = { MaterialTheme.colorScheme.surfaceVariant }
    )

    CollectionStatus.HEALTHY -> MonitoringHealthPresentation(
        titleResource = R.string.dashboard_health_healthy_title,
        descriptionResource = R.string.dashboard_health_healthy_body,
        containerColor = { MaterialTheme.colorScheme.primaryContainer }
    )

    CollectionStatus.DEGRADED -> MonitoringHealthPresentation(
        titleResource = R.string.dashboard_health_degraded_title,
        descriptionResource = R.string.dashboard_health_degraded_body,
        containerColor = { MaterialTheme.colorScheme.secondaryContainer }
    )

    CollectionStatus.UNAVAILABLE -> MonitoringHealthPresentation(
        titleResource = R.string.dashboard_health_unavailable_title,
        descriptionResource = R.string.dashboard_health_unavailable_body,
        containerColor = { MaterialTheme.colorScheme.errorContainer }
    )
}

private fun DataCompleteness.labelResource(): Int = when (this) {
    DataCompleteness.COMPLETE -> R.string.dashboard_completeness_complete
    DataCompleteness.PARTIAL -> R.string.dashboard_completeness_partial
    DataCompleteness.UNKNOWN -> R.string.dashboard_completeness_unknown
}

private fun Duration.formatDashboardDuration(): String {
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

private fun java.time.LocalDate.formatForDashboard(): String =
    format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))

private fun Instant.formatTimeForDashboard(): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(this)
