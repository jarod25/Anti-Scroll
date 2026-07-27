package fr.jarodkohler.antiscroll.domain.observation

import java.time.Instant

enum class CollectionGapReason {
    USAGE_ACCESS_MISSING,
    DEVICE_LOCKED,
    SOURCE_UNAVAILABLE,
    HISTORY_INCOMPLETE,
    PERSISTENCE_FAILURE,
    UNKNOWN
}

data class CollectionCheckpoint(val source: UsageEventSource, val reconciledThrough: Instant, val updatedAt: Instant)

data class CollectionGap(
    val source: UsageEventSource,
    val startInclusive: Instant,
    val endExclusive: Instant?,
    val reason: CollectionGapReason,
    val detectedAt: Instant
) {
    init {
        require(endExclusive == null || startInclusive < endExclusive) {
            "Collection gap end must be after its start"
        }
    }
}
