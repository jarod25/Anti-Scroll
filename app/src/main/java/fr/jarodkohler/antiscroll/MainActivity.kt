package fr.jarodkohler.antiscroll

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import fr.jarodkohler.antiscroll.domain.observation.AccessibilityMonitoringStatus
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus
import fr.jarodkohler.antiscroll.monitoring.permission.MonitoringPermissionReader
import fr.jarodkohler.antiscroll.monitoring.permission.MonitoringPermissionSnapshot
import fr.jarodkohler.antiscroll.permission.PermissionOnboardingScreen
import fr.jarodkohler.antiscroll.ui.theme.AntiScrollTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var permissionReader: MonitoringPermissionReader

    private var permissionSnapshot by mutableStateOf(
        MonitoringPermissionSnapshot(
            usageAccessStatus = UsageAccessStatus.UNAVAILABLE,
            accessibilityStatus = AccessibilityMonitoringStatus.UNSUPPORTED
        )
    )
    private var settingsLaunchFailed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AntiScrollTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PermissionOnboardingScreen(
                        permissionSnapshot = permissionSnapshot,
                        settingsLaunchFailed = settingsLaunchFailed,
                        onOpenUsageAccessSettings = ::openUsageAccessSettings,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        settingsLaunchFailed = false
        permissionSnapshot = permissionReader.read()
    }

    private fun openUsageAccessSettings() {
        settingsLaunchFailed = false

        val intent = listOf(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        ).firstOrNull { candidate ->
            candidate.resolveActivity(packageManager) != null
        }

        if (intent == null) {
            settingsLaunchFailed = true
        } else {
            startActivity(intent)
        }
    }
}
