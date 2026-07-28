package fr.jarodkohler.antiscroll.engine.observation

import fr.jarodkohler.antiscroll.domain.observation.UsageEventSource
import java.time.Duration

data class UsageSessionReconstructionPolicy(
    val source: UsageEventSource,
    val internalTransitionGrace: Duration
) {
    init {
        require(!internalTransitionGrace.isZero && !internalTransitionGrace.isNegative) {
            "Internal activity transition grace must be positive"
        }
    }
}
