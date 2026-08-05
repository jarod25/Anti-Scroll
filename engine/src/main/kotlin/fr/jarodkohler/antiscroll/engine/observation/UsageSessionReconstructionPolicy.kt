package fr.jarodkohler.antiscroll.engine.observation

import fr.jarodkohler.antiscroll.domain.observation.UsageEventSource
import java.time.Duration

data class UsageSessionReconstructionPolicy(
    val source: UsageEventSource,
    val internalTransitionGrace: Duration,
    val openingContinuationGrace: Duration,
    val boundaryLookback: Duration
) {
    init {
        require(!internalTransitionGrace.isZero && !internalTransitionGrace.isNegative) {
            "Internal activity transition grace must be positive"
        }
        require(!openingContinuationGrace.isZero && !openingContinuationGrace.isNegative) {
            "Opening continuation grace must be positive"
        }
        require(openingContinuationGrace > internalTransitionGrace) {
            "Opening continuation grace must exceed the internal transition grace"
        }
        require(!boundaryLookback.isZero && !boundaryLookback.isNegative) {
            "Session boundary lookback must be positive"
        }
        require(boundaryLookback > openingContinuationGrace) {
            "Session boundary lookback must exceed the opening continuation grace"
        }
    }
}
