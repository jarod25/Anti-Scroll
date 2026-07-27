package fr.jarodkohler.antiscroll.domain.observation

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import java.time.Instant

/** Deterministic identity produced during normalization for idempotent persistence. */
@JvmInline
value class UsageEventId(val value: String) {
    init {
        require(value.isNotBlank()) { "Usage event identifier must not be blank" }
    }

    override fun toString(): String = value
}

enum class UsageEventType {
    FOREGROUND_ENTERED,
    FOREGROUND_EXITED
}

enum class UsageEventSource {
    USAGE_STATS,
    ACCESSIBILITY
}

enum class UsageEventReliability {
    OBSERVED,
    INFERRED
}

enum class DataCompleteness {
    COMPLETE,
    PARTIAL,
    UNKNOWN
}

data class NormalizedUsageEvent(
    val id: UsageEventId,
    val packageName: ApplicationPackageName,
    val type: UsageEventType,
    val occurredAt: Instant,
    val source: UsageEventSource,
    val reliability: UsageEventReliability
)

/** Distinguishes a valid empty collection from a source that could not be queried. */
sealed interface UsageCollectionResult {
    val source: UsageEventSource
    val window: ObservationWindow

    data class Collected(
        override val source: UsageEventSource,
        override val window: ObservationWindow,
        val events: List<NormalizedUsageEvent>,
        val completeness: DataCompleteness,
        val incompleteReason: CollectionGapReason? = null
    ) : UsageCollectionResult {
        init {
            require(completeness != DataCompleteness.UNKNOWN) {
                "Collected usage must be complete or partial"
            }
            require(
                (completeness == DataCompleteness.COMPLETE && incompleteReason == null) ||
                    (completeness == DataCompleteness.PARTIAL && incompleteReason != null)
            ) {
                "Partial collection requires a reason and complete collection must not have one"
            }
            require(events.all { event -> event.source == source }) {
                "Collected usage events must match the collection source"
            }
            require(events.all { event -> event.occurredAt in window }) {
                "Collected usage events must belong to the requested window"
            }
        }
    }

    data class Unavailable(
        override val source: UsageEventSource,
        override val window: ObservationWindow,
        val reason: CollectionGapReason
    ) : UsageCollectionResult
}
