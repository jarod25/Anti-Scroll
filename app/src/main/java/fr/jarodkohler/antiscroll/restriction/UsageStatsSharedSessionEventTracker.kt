package fr.jarodkohler.antiscroll.restriction

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionEvent
import fr.jarodkohler.antiscroll.monitoring.usagestats.UsageStatsActivityEventType
import fr.jarodkohler.antiscroll.monitoring.usagestats.UsageStatsEventRecord
import java.time.Duration
import java.time.Instant

internal data class ReconciliationClockSnapshot(val observedAt: Instant, val elapsedRealtime: Duration) {
    init {
        require(!elapsedRealtime.isNegative) { "Elapsed realtime must not be negative" }
    }
}

internal class UsageStatsSharedSessionEventTracker {
    private val activeActivities = mutableMapOf<ApplicationPackageName, MutableSet<String>>()
    private val processedEvents = mutableMapOf<UsageStatsEventKey, Instant>()

    fun markForeground(packageName: ApplicationPackageName) {
        activeActivities.getOrPut(packageName, ::mutableSetOf).add(ACCESSIBILITY_HINT)
    }

    fun accept(
        records: Collection<UsageStatsEventRecord>,
        allowedPackages: Set<ApplicationPackageName>,
        clockSnapshot: ReconciliationClockSnapshot,
        retainFrom: Instant,
        processThrough: Instant,
        settlementWindow: Duration
    ): List<SharedSessionEvent> {
        require(processThrough <= clockSnapshot.observedAt) {
            "UsageStats processing boundary must not follow the clock snapshot"
        }
        require(!settlementWindow.isNegative) {
            "UsageStats settlement window must not be negative"
        }

        processedEvents.entries.removeAll { (_, occurredAt) -> occurredAt < retainFrom }
        if (records.isEmpty() || allowedPackages.isEmpty()) return emptyList()

        val allowedByValue = allowedPackages.associateBy(ApplicationPackageName::value)
        val candidateRecords = records.withIndex().mapNotNull { indexedRecord ->
            val record = indexedRecord.value
            val packageName = allowedByValue[record.packageName] ?: return@mapNotNull null
            val occurredAt = Instant.ofEpochMilli(record.occurredAtEpochMillis)
            if (occurredAt < retainFrom || occurredAt > processThrough) return@mapNotNull null

            val key = UsageStatsEventKey.from(record)
            if (processedEvents.containsKey(key)) return@mapNotNull null
            val elapsedRealtime = elapsedRealtimeFor(
                occurredAt = occurredAt,
                clockSnapshot = clockSnapshot
            ) ?: return@mapNotNull null

            TrackedUsageStatsEvent(
                key = key,
                packageName = packageName,
                activityClassName = record.activityClassName?.takeIf(String::isNotBlank),
                eventType = record.eventType,
                occurredAt = occurredAt,
                elapsedRealtime = elapsedRealtime,
                sourceOrder = indexedRecord.index
            )
        }
        val freshRecords = candidateRecords.sortedWith(
            compareBy<TrackedUsageStatsEvent>(TrackedUsageStatsEvent::occurredAt)
                .thenBy { event -> event.sourceOrder }
        )
        val eventsByPackage = freshRecords.groupBy(TrackedUsageStatsEvent::packageName)

        return eventsByPackage
            .flatMap { (_, packageEvents) ->
                cluster(packageEvents, settlementWindow).mapNotNull(::processCluster)
            }.sortedWith(
                compareBy<SharedSessionEvent>(SharedSessionEvent::observedAt)
                    .thenBy(::eventOrder)
            )
    }

    private fun cluster(
        events: List<TrackedUsageStatsEvent>,
        settlementWindow: Duration
    ): List<List<TrackedUsageStatsEvent>> {
        val clusters = mutableListOf<MutableList<TrackedUsageStatsEvent>>()

        events.forEach { event ->
            val currentCluster = clusters.lastOrNull()
            val firstEvent = currentCluster?.firstOrNull()
            val belongsToCurrentCluster = firstEvent?.let { candidate ->
                Duration.between(candidate.occurredAt, event.occurredAt) <= settlementWindow
            } ?: false

            if (belongsToCurrentCluster) {
                currentCluster.add(event)
            } else {
                clusters += mutableListOf(event)
            }
        }

        return clusters
    }

    private fun processCluster(events: List<TrackedUsageStatsEvent>): SharedSessionEvent? {
        val packageName = events.first().packageName
        val activities = activeActivities.getOrPut(packageName, ::mutableSetOf)
        val wasForeground = activities.isNotEmpty()
        val containsResume = events.any { event -> event.eventType == UsageStatsActivityEventType.RESUMED }

        if (!wasForeground && !containsResume) {
            activeActivities.remove(packageName)
            return null
        }

        events.forEach { event ->
            processedEvents[event.key] = event.occurredAt
            when (event.eventType) {
                UsageStatsActivityEventType.RESUMED -> {
                    activities.remove(ACCESSIBILITY_HINT)
                    activities.add(event.activityKey())
                }

                UsageStatsActivityEventType.PAUSED -> {
                    val removed = activities.remove(event.activityKey())
                    if (!removed) activities.remove(ACCESSIBILITY_HINT)
                }
            }
        }

        val isForeground = activities.isNotEmpty()
        val transitionEvent = when {
            !wasForeground && isForeground -> {
                val resumed = events.first { event ->
                    event.eventType == UsageStatsActivityEventType.RESUMED
                }
                SharedSessionEvent.ApplicationForegrounded(
                    packageName = packageName,
                    observedAt = resumed.occurredAt,
                    elapsedRealtime = resumed.elapsedRealtime
                )
            }

            wasForeground && !isForeground -> {
                val paused = events.last { event ->
                    event.eventType == UsageStatsActivityEventType.PAUSED
                }
                SharedSessionEvent.ApplicationBackgrounded(
                    packageName = packageName,
                    observedAt = paused.occurredAt,
                    elapsedRealtime = paused.elapsedRealtime
                )
            }

            else -> null
        }

        if (!isForeground) activeActivities.remove(packageName)
        return transitionEvent
    }

    private fun elapsedRealtimeFor(occurredAt: Instant, clockSnapshot: ReconciliationClockSnapshot): Duration? {
        val eventAge = Duration.between(occurredAt, clockSnapshot.observedAt)
        if (eventAge.isNegative || eventAge > clockSnapshot.elapsedRealtime) return null
        return clockSnapshot.elapsedRealtime.minus(eventAge)
    }

    private fun eventOrder(event: SharedSessionEvent): Int = when (event) {
        is SharedSessionEvent.ApplicationBackgrounded -> 0
        is SharedSessionEvent.ApplicationForegrounded -> 1
        is SharedSessionEvent.EndRequested -> 2
    }

    private data class TrackedUsageStatsEvent(
        val key: UsageStatsEventKey,
        val packageName: ApplicationPackageName,
        val activityClassName: String?,
        val eventType: UsageStatsActivityEventType,
        val occurredAt: Instant,
        val elapsedRealtime: Duration,
        val sourceOrder: Int
    ) {
        fun activityKey(): String = activityClassName ?: UNKNOWN_ACTIVITY
    }

    private data class UsageStatsEventKey(
        val packageName: String,
        val activityClassName: String?,
        val eventType: UsageStatsActivityEventType,
        val occurredAtEpochMillis: Long
    ) {
        companion object {
            fun from(record: UsageStatsEventRecord): UsageStatsEventKey = UsageStatsEventKey(
                packageName = record.packageName,
                activityClassName = record.activityClassName,
                eventType = record.eventType,
                occurredAtEpochMillis = record.occurredAtEpochMillis
            )
        }
    }

    private companion object {
        const val ACCESSIBILITY_HINT = "<accessibility-hint>"
        const val UNKNOWN_ACTIVITY = "<unknown-activity>"
    }
}
