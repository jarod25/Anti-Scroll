package fr.jarodkohler.antiscroll

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.jarodkohler.antiscroll.applicationconfig.MonitoredApplicationsScreen
import fr.jarodkohler.antiscroll.dashboard.DashboardScreen
import fr.jarodkohler.antiscroll.dashboard.DashboardUiState
import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus
import fr.jarodkohler.antiscroll.permission.PermissionOnboardingScreen

private enum class MainTab(val titleResource: Int) {
    DASHBOARD(R.string.main_tab_dashboard),
    APPLICATIONS(R.string.main_tab_applications)
}

@Composable
fun AntiScrollApp(
    uiState: MainUiState,
    dashboardUiState: DashboardUiState,
    settingsLaunchFailed: Boolean,
    onOpenUsageAccessSettings: () -> Unit,
    onSetApplicationEnabled: (ApplicationPackageName, Boolean) -> Unit,
    onRetryApplications: () -> Unit,
    onRefreshDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.permissionSnapshot.usageAccessStatus == UsageAccessStatus.GRANTED) {
        var selectedTabIndex by rememberSaveable { mutableIntStateOf(MainTab.DASHBOARD.ordinal) }

        Column(modifier = modifier) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                MainTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTabIndex == tab.ordinal,
                        onClick = { selectedTabIndex = tab.ordinal },
                        text = { Text(text = stringResource(tab.titleResource)) }
                    )
                }
            }

            when (MainTab.entries[selectedTabIndex]) {
                MainTab.DASHBOARD -> DashboardScreen(
                    uiState = dashboardUiState,
                    applications = uiState.applications,
                    onRefresh = onRefreshDashboard,
                    modifier = Modifier.weight(1f)
                )

                MainTab.APPLICATIONS -> MonitoredApplicationsScreen(
                    uiState = uiState,
                    settingsLaunchFailed = settingsLaunchFailed,
                    onSetApplicationEnabled = onSetApplicationEnabled,
                    onRetry = onRetryApplications,
                    onReviewUsageAccessSettings = onOpenUsageAccessSettings,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else {
        PermissionOnboardingScreen(
            permissionSnapshot = uiState.permissionSnapshot,
            settingsLaunchFailed = settingsLaunchFailed,
            onOpenUsageAccessSettings = onOpenUsageAccessSettings,
            modifier = modifier
        )
    }
}
