package fr.jarodkohler.antiscroll.observation

import fr.jarodkohler.antiscroll.engine.observation.ObservationReconciliationPolicy
import java.time.Duration

data class ObservationSchedulingPolicy(val periodicInterval: Duration, val retryBackoff: Duration) {
    init {
        require(periodicInterval >= MINIMUM_PERIODIC_INTERVAL) {
            "Observation periodic interval must respect WorkManager's minimum interval"
        }
        require(retryBackoff >= MINIMUM_RETRY_BACKOFF) {
            "Observation retry backoff must respect WorkManager's minimum backoff"
        }
    }

    private companion object {
        val MINIMUM_PERIODIC_INTERVAL: Duration = Duration.ofMinutes(15)
        val MINIMUM_RETRY_BACKOFF: Duration = Duration.ofSeconds(10)
    }
}

object DefaultObservationProfile {
    val reconciliationPolicy = ObservationReconciliationPolicy(
        initialLookback = Duration.ofHours(24),
        replayOverlap = Duration.ofMinutes(5),
        maximumLookback = Duration.ofDays(3)
    )

    val schedulingPolicy = ObservationSchedulingPolicy(
        periodicInterval = Duration.ofMinutes(15),
        retryBackoff = Duration.ofSeconds(30)
    )
}
