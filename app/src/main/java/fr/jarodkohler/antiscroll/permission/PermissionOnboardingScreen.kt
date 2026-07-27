package fr.jarodkohler.antiscroll.permission

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.jarodkohler.antiscroll.R
import fr.jarodkohler.antiscroll.domain.observation.AccessibilityMonitoringStatus
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus
import fr.jarodkohler.antiscroll.monitoring.permission.MonitoringPermissionSnapshot
import fr.jarodkohler.antiscroll.ui.theme.AntiScrollTheme

object PermissionOnboardingTestTags {
    const val USAGE_ACCESS_CARD = "usage_access_card"
    const val USAGE_ACCESS_BUTTON = "usage_access_button"
    const val SETTINGS_ERROR = "settings_error"
    const val ACCESSIBILITY_CARD = "accessibility_card"
}

@Composable
fun PermissionOnboardingScreen(
    permissionSnapshot: MonitoringPermissionSnapshot,
    settingsLaunchFailed: Boolean,
    onOpenUsageAccessSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val usageAccessPresentation = usageAccessPresentation(permissionSnapshot.usageAccessStatus)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Text(
            text = stringResource(R.string.permission_onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.permission_onboarding_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(28.dp))
        PermissionCard(
            title = stringResource(R.string.usage_access_title),
            status = stringResource(usageAccessPresentation.statusRes),
            description = stringResource(usageAccessPresentation.descriptionRes),
            testTag = PermissionOnboardingTestTags.USAGE_ACCESS_CARD,
            action = {
                Button(
                    onClick = onOpenUsageAccessSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PermissionOnboardingTestTags.USAGE_ACCESS_BUTTON)
                ) {
                    Text(text = stringResource(usageAccessPresentation.buttonRes))
                }
            }
        )
        if (settingsLaunchFailed) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_launch_error),
                modifier = Modifier.testTag(PermissionOnboardingTestTags.SETTINGS_ERROR),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        PermissionCard(
            title = stringResource(R.string.accessibility_title),
            status = stringResource(
                accessibilityStatusRes(permissionSnapshot.accessibilityStatus)
            ),
            description = stringResource(R.string.accessibility_description),
            testTag = PermissionOnboardingTestTags.ACCESSIBILITY_CARD,
            supportingLabel = stringResource(R.string.accessibility_optional)
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    status: String,
    description: String,
    testTag: String,
    modifier: Modifier = Modifier,
    supportingLabel: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    supportingLabel?.let { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = status,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            action?.let { content ->
                Spacer(modifier = Modifier.height(16.dp))
                content()
            }
        }
    }
}

private data class UsageAccessPresentation(
    @StringRes val statusRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val buttonRes: Int
)

private fun usageAccessPresentation(status: UsageAccessStatus): UsageAccessPresentation = when (status) {
    UsageAccessStatus.GRANTED -> UsageAccessPresentation(
        statusRes = R.string.usage_access_granted,
        descriptionRes = R.string.usage_access_granted_description,
        buttonRes = R.string.review_usage_access_settings
    )

    UsageAccessStatus.MISSING -> UsageAccessPresentation(
        statusRes = R.string.usage_access_required,
        descriptionRes = R.string.usage_access_required_description,
        buttonRes = R.string.open_usage_access_settings
    )

    UsageAccessStatus.UNAVAILABLE -> UsageAccessPresentation(
        statusRes = R.string.usage_access_unavailable,
        descriptionRes = R.string.usage_access_unavailable_description,
        buttonRes = R.string.open_usage_access_settings
    )

    UsageAccessStatus.ERROR -> UsageAccessPresentation(
        statusRes = R.string.usage_access_error,
        descriptionRes = R.string.usage_access_error_description,
        buttonRes = R.string.review_usage_access_settings
    )
}

@StringRes
private fun accessibilityStatusRes(status: AccessibilityMonitoringStatus): Int = when (status) {
    AccessibilityMonitoringStatus.DISABLED -> R.string.accessibility_disabled

    AccessibilityMonitoringStatus.ENABLED -> R.string.accessibility_enabled

    AccessibilityMonitoringStatus.DISCONNECTED -> R.string.accessibility_disconnected

    AccessibilityMonitoringStatus.UNSUPPORTED -> R.string.accessibility_unsupported

    AccessibilityMonitoringStatus.ERROR -> R.string.accessibility_error
}

@Preview(showBackground = true)
@Composable
private fun PermissionOnboardingScreenPreview() {
    AntiScrollTheme {
        PermissionOnboardingScreen(
            permissionSnapshot = MonitoringPermissionSnapshot(
                usageAccessStatus = UsageAccessStatus.MISSING,
                accessibilityStatus = AccessibilityMonitoringStatus.UNSUPPORTED
            ),
            settingsLaunchFailed = false,
            onOpenUsageAccessSettings = {}
        )
    }
}
