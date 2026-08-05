package fr.jarodkohler.antiscroll.monitoring.accessibility

import android.view.accessibility.AccessibilityEvent
import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.monitoring.ForegroundApplicationSignal
import java.time.Duration
import java.time.Instant

internal fun foregroundApplicationSignalFor(
    eventType: Int,
    rawPackageName: CharSequence?,
    monitoredPackages: Set<ApplicationPackageName>,
    observedAt: Instant,
    elapsedRealtime: Duration
): ForegroundApplicationSignal? {
    if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
        return null
    }

    val packageName = runCatching {
        ApplicationPackageName(rawPackageName?.toString().orEmpty())
    }.getOrNull() ?: return null

    if (packageName !in monitoredPackages) {
        return null
    }

    return ForegroundApplicationSignal(
        packageName = packageName,
        observedAt = observedAt,
        elapsedRealtime = elapsedRealtime
    )
}

internal fun accessibilityPackageFilter(
    monitoredPackages: Set<ApplicationPackageName>,
    applicationPackageName: String
): Array<String> = monitoredPackages
    .map(ApplicationPackageName::value)
    .sorted()
    .ifEmpty { listOf(applicationPackageName) }
    .toTypedArray()
