package fr.jarodkohler.antiscroll.monitoring.accessibility

import fr.jarodkohler.antiscroll.domain.monitoring.ForegroundApplicationSignal
import fr.jarodkohler.antiscroll.domain.monitoring.ForegroundApplicationSignalSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class AccessibilityForegroundApplicationSignalSource @Inject constructor() :
    ForegroundApplicationSignalSource {
    private val mutableSignals = MutableSharedFlow<ForegroundApplicationSignal>(
        extraBufferCapacity = SIGNAL_BUFFER_CAPACITY
    )

    override val signals: Flow<ForegroundApplicationSignal> = mutableSignals.asSharedFlow()

    fun publish(signal: ForegroundApplicationSignal): Boolean = mutableSignals.tryEmit(signal)

    private companion object {
        const val SIGNAL_BUFFER_CAPACITY = 64
    }
}
