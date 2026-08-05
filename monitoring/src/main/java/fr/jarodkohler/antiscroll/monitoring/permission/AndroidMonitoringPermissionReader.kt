package fr.jarodkohler.antiscroll.monitoring.permission

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.view.accessibility.AccessibilityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.jarodkohler.antiscroll.domain.observation.AccessibilityMonitoringStatus
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus
import fr.jarodkohler.antiscroll.monitoring.accessibility.AccessibilityMonitoringConnection
import fr.jarodkohler.antiscroll.monitoring.accessibility.AntiScrollAccessibilityService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidMonitoringPermissionReader
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val accessibilityConnection: AccessibilityMonitoringConnection
) : MonitoringPermissionReader {
    override fun read(): MonitoringPermissionSnapshot = MonitoringPermissionSnapshot(
        usageAccessStatus = readUsageAccessStatus(),
        accessibilityStatus = readAccessibilityStatus()
    )

    private fun readUsageAccessStatus(): UsageAccessStatus {
        val appOpsManager = context.getSystemService(AppOpsManager::class.java)
            ?: return UsageAccessStatus.UNAVAILABLE

        return runCatching {
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }.fold(
            onSuccess = ::usageAccessStatusForMode,
            onFailure = { UsageAccessStatus.ERROR }
        )
    }

    private fun readAccessibilityStatus(): AccessibilityMonitoringStatus {
        val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)
            ?: return AccessibilityMonitoringStatus.UNSUPPORTED

        return runCatching {
            val expectedComponent = ComponentName(
                context,
                AntiScrollAccessibilityService::class.java
            )
            val isEnabled = accessibilityManager
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { serviceInfo ->
                    val androidServiceInfo = serviceInfo.resolveInfo?.serviceInfo
                        ?: return@any false
                    ComponentName(
                        androidServiceInfo.packageName,
                        androidServiceInfo.name
                    ) == expectedComponent
                }

            accessibilityMonitoringStatus(
                isEnabled = isEnabled,
                isConnected = accessibilityConnection.isConnected
            )
        }.getOrElse { AccessibilityMonitoringStatus.ERROR }
    }
}

internal fun usageAccessStatusForMode(mode: Int): UsageAccessStatus = if (mode == AppOpsManager.MODE_ALLOWED) {
    UsageAccessStatus.GRANTED
} else {
    UsageAccessStatus.MISSING
}

internal fun accessibilityMonitoringStatus(
    isEnabled: Boolean,
    isConnected: Boolean
): AccessibilityMonitoringStatus = when {
    !isEnabled -> AccessibilityMonitoringStatus.DISABLED
    isConnected -> AccessibilityMonitoringStatus.ENABLED
    else -> AccessibilityMonitoringStatus.DISCONNECTED
}
