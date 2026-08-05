package fr.jarodkohler.antiscroll.monitoring.permission

import fr.jarodkohler.antiscroll.domain.observation.AccessibilityMonitoringStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityMonitoringStatusResolverTest {
    @Test
    fun disabledServiceIsReportedAsDisabled() {
        assertEquals(
            AccessibilityMonitoringStatus.DISABLED,
            accessibilityMonitoringStatus(isEnabled = false, isConnected = false)
        )
    }

    @Test
    fun enabledConnectedServiceIsReportedAsEnabled() {
        assertEquals(
            AccessibilityMonitoringStatus.ENABLED,
            accessibilityMonitoringStatus(isEnabled = true, isConnected = true)
        )
    }

    @Test
    fun enabledServiceWithoutConnectionIsReportedAsDisconnected() {
        assertEquals(
            AccessibilityMonitoringStatus.DISCONNECTED,
            accessibilityMonitoringStatus(isEnabled = true, isConnected = false)
        )
    }
}
