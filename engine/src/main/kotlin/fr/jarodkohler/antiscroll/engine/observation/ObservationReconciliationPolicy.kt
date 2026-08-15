package fr.jarodkohler.antiscroll.engine.observation

import java.time.Duration

data class ObservationReconciliationPolicy(
    val initialLookback: Duration,
    val replayOverlap: Duration,
    val maximumLookback: Duration
) {
    init {
        require(initialLookback.isPositive()) { "Initial observation lookback must be positive" }
        require(replayOverlap.isPositive()) { "Observation replay overlap must be positive" }
        require(maximumLookback.isPositive()) { "Maximum observation lookback must be positive" }
        require(initialLookback <= maximumLookback) {
            "Initial observation lookback must not exceed the maximum lookback"
        }
        require(replayOverlap < maximumLookback) {
            "Observation replay overlap must be shorter than the maximum lookback"
        }
    }
}
