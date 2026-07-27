package fr.jarodkohler.antiscroll.monitoring.permission

import fr.jarodkohler.antiscroll.domain.observation.AccessibilityMonitoringStatus
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus

data class MonitoringPermissionSnapshot(
    val usageAccessStatus: UsageAccessStatus,
    val accessibilityStatus: AccessibilityMonitoringStatus
)

interface MonitoringPermissionReader {
    fun read(): MonitoringPermissionSnapshot
}
