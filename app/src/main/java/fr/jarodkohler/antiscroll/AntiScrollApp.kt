package fr.jarodkohler.antiscroll

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.jarodkohler.antiscroll.applicationconfig.MonitoredApplicationsScreen
import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus
import fr.jarodkohler.antiscroll.permission.PermissionOnboardingScreen

@Composable
fun AntiScrollApp(
    uiState: MainUiState,
    settingsLaunchFailed: Boolean,
    onOpenUsageAccessSettings: () -> Unit,
    onSetApplicationEnabled: (ApplicationPackageName, Boolean) -> Unit,
    onRetryApplications: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.permissionSnapshot.usageAccessStatus == UsageAccessStatus.GRANTED) {
        MonitoredApplicationsScreen(
            uiState = uiState,
            settingsLaunchFailed = settingsLaunchFailed,
            onSetApplicationEnabled = onSetApplicationEnabled,
            onRetry = onRetryApplications,
            onReviewUsageAccessSettings = onOpenUsageAccessSettings,
            modifier = modifier
        )
    } else {
        PermissionOnboardingScreen(
            permissionSnapshot = uiState.permissionSnapshot,
            settingsLaunchFailed = settingsLaunchFailed,
            onOpenUsageAccessSettings = onOpenUsageAccessSettings,
            modifier = modifier
        )
    }
}
