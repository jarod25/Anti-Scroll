package fr.jarodkohler.antiscroll.monitoring.accessibility

import android.view.accessibility.AccessibilityEvent
import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibilityForegroundSignalMapperTest {
    private val instagram = ApplicationPackageName("com.instagram.android")
    private val observedAt = Instant.parse("2026-08-05T12:00:00Z")
    private val elapsedRealtime = Duration.ofHours(2)

    @Test
    fun monitoredWindowStateChangeCreatesSignal() {
        val signal = foregroundApplicationSignalFor(
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            rawPackageName = instagram.value,
            monitoredPackages = setOf(instagram),
            observedAt = observedAt,
            elapsedRealtime = elapsedRealtime
        )

        requireNotNull(signal)
        assertEquals(instagram, signal.packageName)
        assertEquals(observedAt, signal.observedAt)
        assertEquals(elapsedRealtime, signal.elapsedRealtime)
    }

    @Test
    fun unrelatedEventsAndPackagesAreIgnored() {
        assertNull(
            foregroundApplicationSignalFor(
                eventType = AccessibilityEvent.TYPE_VIEW_CLICKED,
                rawPackageName = instagram.value,
                monitoredPackages = setOf(instagram),
                observedAt = observedAt,
                elapsedRealtime = elapsedRealtime
            )
        )
        assertNull(
            foregroundApplicationSignalFor(
                eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                rawPackageName = "com.example.communication",
                monitoredPackages = setOf(instagram),
                observedAt = observedAt,
                elapsedRealtime = elapsedRealtime
            )
        )
    }

    @Test
    fun emptyPackageFilterUsesOnlyAntiScrollPackage() {
        assertArrayEquals(
            arrayOf("fr.jarodkohler.antiscroll"),
            accessibilityPackageFilter(
                monitoredPackages = emptySet(),
                applicationPackageName = "fr.jarodkohler.antiscroll"
            )
        )
    }
}
