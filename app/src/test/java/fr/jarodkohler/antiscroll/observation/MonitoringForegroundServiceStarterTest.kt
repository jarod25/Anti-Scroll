package fr.jarodkohler.antiscroll.observation

import fr.jarodkohler.antiscroll.domain.observation.AccessibilityMonitoringStatus
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus
import fr.jarodkohler.antiscroll.monitoring.permission.MonitoringPermissionSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringForegroundServiceStarterTest {
    @Test
    fun startsWhenUsageAccessIsGrantedAndAccessibilityIsConnected() {
        assertTrue(eligible(UsageAccessStatus.GRANTED, AccessibilityMonitoringStatus.ENABLED))
    }

    @Test
    fun startsWhenAccessibilityIsEnabledButNotYetConnectedAfterBoot() {
        assertTrue(eligible(UsageAccessStatus.GRANTED, AccessibilityMonitoringStatus.DISCONNECTED))
    }

    @Test
    fun doesNotStartWithoutUsageAccess() {
        assertFalse(eligible(UsageAccessStatus.MISSING, AccessibilityMonitoringStatus.ENABLED))
        assertFalse(eligible(UsageAccessStatus.UNAVAILABLE, AccessibilityMonitoringStatus.DISCONNECTED))
        assertFalse(eligible(UsageAccessStatus.ERROR, AccessibilityMonitoringStatus.ENABLED))
    }

    @Test
    fun doesNotStartWhenAccessibilityIsNotEnabled() {
        assertFalse(eligible(UsageAccessStatus.GRANTED, AccessibilityMonitoringStatus.DISABLED))
        assertFalse(eligible(UsageAccessStatus.GRANTED, AccessibilityMonitoringStatus.UNSUPPORTED))
        assertFalse(eligible(UsageAccessStatus.GRANTED, AccessibilityMonitoringStatus.ERROR))
    }

    private fun eligible(
        usageAccessStatus: UsageAccessStatus,
        accessibilityStatus: AccessibilityMonitoringStatus
    ) = shouldStartMonitoringForegroundService(
        MonitoringPermissionSnapshot(
            usageAccessStatus = usageAccessStatus,
            accessibilityStatus = accessibilityStatus
        )
    )
}
