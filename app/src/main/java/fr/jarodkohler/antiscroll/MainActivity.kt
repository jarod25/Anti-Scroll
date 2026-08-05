package fr.jarodkohler.antiscroll

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import fr.jarodkohler.antiscroll.dashboard.DashboardViewModel
import fr.jarodkohler.antiscroll.observation.ObservationWorkScheduler
import fr.jarodkohler.antiscroll.ui.theme.AntiScrollTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private var settingsLaunchFailed by mutableStateOf(false)

    @Inject
    lateinit var observationWorkScheduler: ObservationWorkScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observationWorkScheduler.ensurePeriodicReconciliation()
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val dashboardUiState by dashboardViewModel.uiState.collectAsState()

            AntiScrollTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AntiScrollApp(
                        uiState = uiState,
                        dashboardUiState = dashboardUiState,
                        settingsLaunchFailed = settingsLaunchFailed,
                        onOpenUsageAccessSettings = ::openUsageAccessSettings,
                        onOpenAccessibilitySettings = ::openAccessibilitySettings,
                        onSetApplicationEnabled = viewModel::setApplicationEnabled,
                        onRetryApplications = viewModel::refresh,
                        onRefreshDashboard = ::refreshObservation,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        observationWorkScheduler.requestImmediateReconciliation()
        settingsLaunchFailed = false
        viewModel.refresh()
        dashboardViewModel.refreshDate()
    }

    private fun refreshObservation() {
        observationWorkScheduler.requestImmediateReconciliation()
        viewModel.refresh()
        dashboardViewModel.refreshDate()
    }

    private fun openUsageAccessSettings() {
        openFirstAvailableSettings(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
    }

    private fun openAccessibilitySettings() {
        openFirstAvailableSettings(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
    }

    private fun openFirstAvailableSettings(vararg candidates: Intent) {
        settingsLaunchFailed = false
        val intent = candidates.firstOrNull { candidate ->
            candidate.resolveActivity(packageManager) != null
        }

        if (intent == null) {
            settingsLaunchFailed = true
        } else {
            startActivity(intent)
        }
    }
}
