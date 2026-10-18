package fr.jarodkohler.antiscroll.observation

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.jarodkohler.antiscroll.domain.observation.AccessibilityMonitoringStatus
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus
import fr.jarodkohler.antiscroll.monitoring.lifetime.MonitoringForegroundService
import fr.jarodkohler.antiscroll.monitoring.permission.MonitoringPermissionReader
import fr.jarodkohler.antiscroll.monitoring.permission.MonitoringPermissionSnapshot
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitoringForegroundServiceStarter
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val permissionReader: MonitoringPermissionReader
) {
    fun startIfEligible(): Boolean {
        if (!shouldStartMonitoringForegroundService(permissionReader.read())) return false

        return try {
            MonitoringForegroundService.start(context)
            true
        } catch (error: SecurityException) {
            Log.w(TAG, "Android denied the monitoring foreground service start", error)
            false
        } catch (error: IllegalStateException) {
            Log.w(TAG, "Android did not allow the monitoring foreground service start", error)
            false
        }
    }

    private companion object {
        const val TAG = "AntiScrollMonitoring"
    }
}

internal fun shouldStartMonitoringForegroundService(snapshot: MonitoringPermissionSnapshot): Boolean {
    if (snapshot.usageAccessStatus != UsageAccessStatus.GRANTED) return false

    return when (snapshot.accessibilityStatus) {
        AccessibilityMonitoringStatus.ENABLED,
        AccessibilityMonitoringStatus.DISCONNECTED -> true

        AccessibilityMonitoringStatus.DISABLED,
        AccessibilityMonitoringStatus.UNSUPPORTED,
        AccessibilityMonitoringStatus.ERROR -> false
    }
}
