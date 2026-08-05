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
    const val PRIVACY_SUMMARY_CARD = "privacy_summary_card"
    const val USAGE_ACCESS_CARD = "usage_access_card"
    const val USAGE_ACCESS_BUTTON = "usage_access_button"
    const val SETTINGS_ERROR = "settings_error"
    const val ACCESSIBILITY_CARD = "accessibility_card"
    const val ACCESSIBILITY_BUTTON = "accessibility_button"
}

@Composable
fun PermissionOnboardingScreen(
    permissionSnapshot: MonitoringPermissionSnapshot,
    settingsLaunchFailed: Boolean,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val usageAccessPresentation = usageAccessPresentation(permissionSnapshot.usageAccessStatus)
    val accessibilityPresentation = accessibilityPresentation(
        permissionSnapshot.accessibilityStatus
    )

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
        Spacer(modifier = Modifier.height(20.dp))
        PrivacySummaryCard()
        Spacer(modifier = Modifier.height(16.dp))
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
        Spacer(modifier = Modifier.height(16.dp))
        PermissionCard(
            title = stringResource(R.string.accessibility_title),
            status = stringResource(accessibilityPresentation.statusRes),
            description = stringResource(accessibilityPresentation.descriptionRes),
            testTag = PermissionOnboardingTestTags.ACCESSIBILITY_CARD,
            supportingLabel = stringResource(R.string.accessibility_realtime_label),
            action = {
                Button(
                    onClick = onOpenAccessibilitySettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PermissionOnboardingTestTags.ACCESSIBILITY_BUTTON)
                ) {
                    Text(text = stringResource(accessibilityPresentation.buttonRes))
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
    }
}

@Composable
private fun PrivacySummaryCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(PermissionOnboardingTestTags.PRIVACY_SUMMARY_CARD)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.usage_access_privacy_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            PrivacyPoint(text = stringResource(R.string.usage_access_privacy_usage))
            PrivacyPoint(text = stringResource(R.string.usage_access_privacy_exclusions))
            PrivacyPoint(text = stringResource(R.string.usage_access_privacy_local))
        }
    }
}

@Composable
private fun PrivacyPoint(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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

private data class PermissionPresentation(
    @StringRes val statusRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val buttonRes: Int
)

private fun usageAccessPresentation(status: UsageAccessStatus): PermissionPresentation = when (status) {
    UsageAccessStatus.GRANTED -> PermissionPresentation(
        statusRes = R.string.usage_access_granted,
        descriptionRes = R.string.usage_access_granted_description,
        buttonRes = R.string.review_usage_access_settings
    )

    UsageAccessStatus.MISSING -> PermissionPresentation(
        statusRes = R.string.usage_access_required,
        descriptionRes = R.string.usage_access_required_description,
        buttonRes = R.string.open_usage_access_settings
    )

    UsageAccessStatus.UNAVAILABLE -> PermissionPresentation(
        statusRes = R.string.usage_access_unavailable,
        descriptionRes = R.string.usage_access_unavailable_description,
        buttonRes = R.string.open_usage_access_settings
    )

    UsageAccessStatus.ERROR -> PermissionPresentation(
        statusRes = R.string.usage_access_error,
        descriptionRes = R.string.usage_access_error_description,
        buttonRes = R.string.review_usage_access_settings
    )
}

private fun accessibilityPresentation(
    status: AccessibilityMonitoringStatus
): PermissionPresentation = when (status) {
    AccessibilityMonitoringStatus.DISABLED -> PermissionPresentation(
        statusRes = R.string.accessibility_disabled,
        descriptionRes = R.string.accessibility_disabled_description,
        buttonRes = R.string.open_accessibility_settings
    )

    AccessibilityMonitoringStatus.ENABLED -> PermissionPresentation(
        statusRes = R.string.accessibility_enabled,
        descriptionRes = R.string.accessibility_enabled_description,
        buttonRes = R.string.review_accessibility_settings
    )

    AccessibilityMonitoringStatus.DISCONNECTED -> PermissionPresentation(
        statusRes = R.string.accessibility_disconnected,
        descriptionRes = R.string.accessibility_disconnected_description,
        buttonRes = R.string.review_accessibility_settings
    )

    AccessibilityMonitoringStatus.UNSUPPORTED -> PermissionPresentation(
        statusRes = R.string.accessibility_unsupported,
        descriptionRes = R.string.accessibility_unsupported_description,
        buttonRes = R.string.open_accessibility_settings
    )

    AccessibilityMonitoringStatus.ERROR -> PermissionPresentation(
        statusRes = R.string.accessibility_error,
        descriptionRes = R.string.accessibility_error_description,
        buttonRes = R.string.review_accessibility_settings
    )
}

@Preview(showBackground = true)
@Composable
private fun PermissionOnboardingScreenPreview() {
    AntiScrollTheme {
        PermissionOnboardingScreen(
            permissionSnapshot = MonitoringPermissionSnapshot(
                usageAccessStatus = UsageAccessStatus.MISSING,
                accessibilityStatus = AccessibilityMonitoringStatus.DISABLED
            ),
            settingsLaunchFailed = false,
            onOpenUsageAccessSettings = {},
            onOpenAccessibilitySettings = {}
        )
    }
}
