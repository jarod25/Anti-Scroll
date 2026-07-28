package fr.jarodkohler.antiscroll.applicationconfig

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.jarodkohler.antiscroll.MainUiState
import fr.jarodkohler.antiscroll.MonitoredApplicationUiModel
import fr.jarodkohler.antiscroll.R
import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.ui.theme.AntiScrollTheme

object MonitoredApplicationsTestTags {
    const val LIST = "monitored_applications_list"
    const val LOADING = "monitored_applications_loading"
    const val EMPTY = "monitored_applications_empty"
    const val LOAD_ERROR = "monitored_applications_load_error"
    const val SAVE_ERROR = "monitored_applications_save_error"

    fun switch(packageName: ApplicationPackageName): String =
        "monitored_application_switch_${packageName.value}"
}

@Composable
fun MonitoredApplicationsScreen(
    uiState: MainUiState,
    onSetApplicationEnabled: (ApplicationPackageName, Boolean) -> Unit,
    onRetry: () -> Unit,
    onReviewUsageAccessSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Text(
            text = stringResource(R.string.monitored_applications_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.monitored_applications_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
            onClick = onReviewUsageAccessSettings,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(text = stringResource(R.string.review_usage_access_settings))
        }

        if (uiState.configurationSaveFailed) {
            Text(
                text = stringResource(R.string.monitored_applications_save_error),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MonitoredApplicationsTestTags.SAVE_ERROR),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        when {
            uiState.isLoadingApplications -> LoadingApplications(
                modifier = Modifier.weight(1f)
            )

            uiState.applicationLoadFailed -> ApplicationLoadError(
                onRetry = onRetry,
                modifier = Modifier.weight(1f)
            )

            uiState.applications.isEmpty() -> EmptyApplications(
                modifier = Modifier.weight(1f)
            )

            else -> LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag(MonitoredApplicationsTestTags.LIST),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = uiState.applications,
                    key = { application -> application.packageName.value }
                ) { application ->
                    MonitoredApplicationRow(
                        application = application,
                        onEnabledChange = { isEnabled ->
                            onSetApplicationEnabled(application.packageName, isEnabled)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MonitoredApplicationRow(
    application: MonitoredApplicationUiModel,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ApplicationIcon(application = application)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = application.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = application.packageName.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = application.isEnabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.testTag(
                    MonitoredApplicationsTestTags.switch(application.packageName)
                )
            )
        }
    }
}

@Composable
private fun ApplicationIcon(
    application: MonitoredApplicationUiModel,
    modifier: Modifier = Modifier
) {
    val icon = application.icon
    if (icon == null) {
        Surface(
            modifier = modifier.size(44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = application.label.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else {
        Image(
            bitmap = icon,
            contentDescription = application.label,
            modifier = modifier.size(44.dp)
        )
    }
}

@Composable
private fun LoadingApplications(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag(MonitoredApplicationsTestTags.LOADING),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyApplications(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag(MonitoredApplicationsTestTags.EMPTY),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.monitored_applications_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ApplicationLoadError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(MonitoredApplicationsTestTags.LOAD_ERROR),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.monitored_applications_load_error),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Text(text = stringResource(R.string.retry))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MonitoredApplicationsScreenPreview() {
    AntiScrollTheme {
        MonitoredApplicationsScreen(
            uiState = MainUiState(
                applications = listOf(
                    MonitoredApplicationUiModel(
                        packageName = ApplicationPackageName("com.instagram.android"),
                        label = "Instagram",
                        icon = null,
                        isEnabled = true
                    ),
                    MonitoredApplicationUiModel(
                        packageName = ApplicationPackageName("com.reddit.frontpage"),
                        label = "Reddit",
                        icon = null,
                        isEnabled = false
                    )
                )
            ),
            onSetApplicationEnabled = { _, _ -> },
            onRetry = {},
            onReviewUsageAccessSettings = {}
        )
    }
}
