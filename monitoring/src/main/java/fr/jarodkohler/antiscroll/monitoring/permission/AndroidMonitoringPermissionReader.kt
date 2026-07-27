package fr.jarodkohler.antiscroll.monitoring.permission

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.jarodkohler.antiscroll.domain.observation.AccessibilityMonitoringStatus
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidMonitoringPermissionReader
@Inject
constructor(@ApplicationContext private val context: Context) :
    MonitoringPermissionReader {
    override fun read(): MonitoringPermissionSnapshot = MonitoringPermissionSnapshot(
        usageAccessStatus = readUsageAccessStatus(),
        accessibilityStatus = AccessibilityMonitoringStatus.UNSUPPORTED
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
}

internal fun usageAccessStatusForMode(mode: Int): UsageAccessStatus = if (mode == AppOpsManager.MODE_ALLOWED) {
    UsageAccessStatus.GRANTED
} else {
    UsageAccessStatus.MISSING
}
