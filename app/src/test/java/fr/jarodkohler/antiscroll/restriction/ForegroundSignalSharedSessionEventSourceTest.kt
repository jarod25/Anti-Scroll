package fr.jarodkohler.antiscroll.restriction

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.monitoring.ForegroundApplicationSignal
import fr.jarodkohler.antiscroll.domain.monitoring.ForegroundApplicationSignalSource
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionEvent
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundSignalSharedSessionEventSourceTest {
    @Test
    fun foregroundSignalBecomesExplicitSessionEventWithoutChangingTimestamps() = runTest {
        val mutableSignals = MutableSharedFlow<ForegroundApplicationSignal>(extraBufferCapacity = 1)
        val source = ForegroundSignalSharedSessionEventSource(
            object : ForegroundApplicationSignalSource {
                override val signals: Flow<ForegroundApplicationSignal> = mutableSignals.asSharedFlow()
            }
        )
        val packageName = ApplicationPackageName("com.instagram.android")
        val observedAt = Instant.parse("2026-08-05T10:00:00Z")
        val elapsedRealtime = Duration.ofHours(2)

        val eventDeferred = async { source.events.first() }
        runCurrent()
        mutableSignals.tryEmit(
            ForegroundApplicationSignal(
                packageName = packageName,
                observedAt = observedAt,
                elapsedRealtime = elapsedRealtime
            )
        )
        val event = eventDeferred.await() as SharedSessionEvent.ApplicationForegrounded

        assertEquals(packageName, event.packageName)
        assertEquals(observedAt, event.observedAt)
        assertEquals(elapsedRealtime, event.elapsedRealtime)
    }
}
