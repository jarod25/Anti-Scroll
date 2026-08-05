package fr.jarodkohler.antiscroll.domain.monitoring

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow

data class ForegroundApplicationSignal(
    val packageName: ApplicationPackageName,
    val observedAt: Instant,
    val elapsedRealtime: Duration
) {
    init {
        require(!elapsedRealtime.isNegative) { "Elapsed realtime must not be negative" }
    }
}

interface ForegroundApplicationSignalSource {
    val signals: Flow<ForegroundApplicationSignal>
}
