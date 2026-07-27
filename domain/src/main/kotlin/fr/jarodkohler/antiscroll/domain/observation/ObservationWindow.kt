package fr.jarodkohler.antiscroll.domain.observation

import java.time.Instant

/** Half-open wall-clock range queried from an observation source. */
data class ObservationWindow(val startInclusive: Instant, val endExclusive: Instant) {
    init {
        require(startInclusive < endExclusive) {
            "Observation window start must be before its end"
        }
    }

    operator fun contains(instant: Instant): Boolean = instant >= startInclusive && instant < endExclusive
}
