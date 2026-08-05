package fr.jarodkohler.antiscroll.monitoring.accessibility

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessibilityMonitoringConnection @Inject constructor() {
    private val connected = AtomicBoolean(false)

    val isConnected: Boolean
        get() = connected.get()

    fun markConnected() {
        connected.set(true)
    }

    fun markDisconnected() {
        connected.set(false)
    }
}
