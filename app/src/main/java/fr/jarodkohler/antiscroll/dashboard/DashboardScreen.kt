package fr.jarodkohler.antiscroll.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.jarodkohler.antiscroll.MonitoredApplicationUiModel
import fr.jarodkohler.antiscroll.R
import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import java.time.Duration

object DashboardTestTags {
    const val SUMMARY = "dashboard_summary"
    const val HEALTH = "dashboard_health"
    const val BASELINE = "dashboard_baseline"
    const val EMPTY = "dashboard_empty"
    const val REFRESH = "dashboard_refresh"

    fun application(packageName: ApplicationPackageName): String = "dashboard_application_${packageName.value}"
}

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

        item {
            ObservationBaselineCard(
                baseline = uiState.observationBaseline,
                modifier = Modifier.testTag(DashboardTestTags.BASELINE)
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
