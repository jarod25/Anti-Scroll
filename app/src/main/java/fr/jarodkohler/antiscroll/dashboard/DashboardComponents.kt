package fr.jarodkohler.antiscroll.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.jarodkohler.antiscroll.R
import fr.jarodkohler.antiscroll.domain.observation.CollectionStatus
import fr.jarodkohler.antiscroll.ui.components.ApplicationIcon
import java.time.Duration
import java.time.Instant

@Composable
internal fun DashboardHeader(dateLabel: String, onRefresh: () -> Unit) {
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
internal fun DailySummaryCard(totalDuration: Duration, totalOpenings: Int) {
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
internal fun MonitoringHealthCard(collectionStatus: CollectionStatus, lastSuccessfulReconciliationAt: Instant?) {
    val presentation = collectionStatus.dashboardPresentation()
    val containerColor = when (presentation.tone) {
        MonitoringHealthTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
        MonitoringHealthTone.HEALTHY -> MaterialTheme.colorScheme.primaryContainer
        MonitoringHealthTone.DEGRADED -> MaterialTheme.colorScheme.secondaryContainer
        MonitoringHealthTone.UNAVAILABLE -> MaterialTheme.colorScheme.errorContainer
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DashboardTestTags.HEALTH),
        shape = MaterialTheme.shapes.medium,
        color = containerColor
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
internal fun DashboardApplicationRow(
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
