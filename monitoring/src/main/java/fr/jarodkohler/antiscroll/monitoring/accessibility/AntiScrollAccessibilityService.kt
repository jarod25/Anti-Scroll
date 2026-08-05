package fr.jarodkohler.antiscroll.monitoring.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.observation.MonitoredApplicationRepository
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class AntiScrollAccessibilityService : AccessibilityService() {
    @Inject
    lateinit var monitoredApplicationRepository: MonitoredApplicationRepository

    @Inject
    lateinit var signalSource: AccessibilityForegroundApplicationSignalSource

    @Inject
    lateinit var connection: AccessibilityMonitoringConnection

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitoredPackages = emptySet<ApplicationPackageName>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        connection.markConnected()

        monitoredApplicationRepository.observeAll()
            .map { applications ->
                applications
                    .asSequence()
                    .filter { application -> application.isEnabled }
                    .map { application -> application.packageName }
                    .toSet()
            }
            .distinctUntilChanged()
            .onEach(::updateMonitoredPackages)
            .catch { updateMonitoredPackages(emptySet()) }
            .launchIn(serviceScope)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val signal = foregroundApplicationSignalFor(
            eventType = event.eventType,
            rawPackageName = event.packageName,
            monitoredPackages = monitoredPackages,
            observedAt = Instant.now(),
            elapsedRealtime = Duration.ofMillis(SystemClock.elapsedRealtime())
        ) ?: return

        signalSource.publish(signal)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        connection.markDisconnected()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        connection.markDisconnected()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun updateMonitoredPackages(packages: Set<ApplicationPackageName>) {
        monitoredPackages = packages
        val updatedServiceInfo = serviceInfo ?: return
        updatedServiceInfo.packageNames = accessibilityPackageFilter(
            monitoredPackages = packages,
            applicationPackageName = packageName
        )
        setServiceInfo(updatedServiceInfo)
    }
}
