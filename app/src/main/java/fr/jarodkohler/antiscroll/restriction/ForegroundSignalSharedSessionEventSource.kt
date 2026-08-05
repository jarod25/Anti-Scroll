package fr.jarodkohler.antiscroll.restriction

import fr.jarodkohler.antiscroll.domain.monitoring.ForegroundApplicationSignalSource
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionEvent
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionEventSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ForegroundSignalSharedSessionEventSource
@Inject
constructor(signalSource: ForegroundApplicationSignalSource) : SharedSessionEventSource {
    override val events: Flow<SharedSessionEvent> = signalSource.signals.map { signal ->
        SharedSessionEvent.ApplicationForegrounded(
            packageName = signal.packageName,
            observedAt = signal.observedAt,
            elapsedRealtime = signal.elapsedRealtime
        )
    }
}
