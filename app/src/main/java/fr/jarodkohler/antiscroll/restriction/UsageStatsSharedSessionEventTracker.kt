package fr.jarodkohler.antiscroll.restriction

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionEvent
import fr.jarodkohler.antiscroll.monitoring.usagestats.UsageStatsActivityEventType
import fr.jarodkohler.antiscroll.monitoring.usagestats.UsageStatsEventRecord
import java.time.Duration
import java.time.Instant

internal data class ReconciliationClockSnapshot(
    val observedAt: Instant,
    val elapsedRealtime: Duration
) {
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
        retainFrom: Instant
    ): List<SharedSessionEvent> {
        processedEvents.entries.removeAll { (_, occurredAt) -> occurredAt < retainFrom }
        if (records.isEmpty() || allowedPackages.isEmpty()) return emptyList()

        val allowedByValue = allowedPackages.associateBy(ApplicationPackageName::value)
        val freshRecords = records.mapNotNull { record ->
            val packageName = allowedByValue[record.packageName] ?: return@mapNotNull null
            val occurredAt = Instant.ofEpochMilli(record.occurredAtEpochMillis)
            if (occurredAt < retainFrom || occurredAt > clockSnapshot.observedAt) {
                return@mapNotNull null
            }

            val key = UsageStatsEventKey.from(record)
            if (processedEvents.containsKey(key)) return@mapNotNull null

            TrackedUsageStatsEvent(
                key = key,
                packageName = packageName,
                activityClassName = record.activityClassName?.takeIf(String::isNotBlank),
                eventType = record.eventType,
                occurredAt = occurredAt
            )
        }.sortedWith(
            compareBy<TrackedUsageStatsEvent>(TrackedUsageStatsEvent::occurredAt)
                .thenBy { event -> event.packageName.value }
                .thenBy { event -> event.eventType.ordinal }
                .thenBy { event -> event.activityClassName.orEmpty() }
        )

        val emittedEvents = mutableListOf<SharedSessionEvent>()
        freshRecords.groupBy { event -> event.occurredAt to event.packageName }
            .toSortedMap(compareBy<Pair<Instant, ApplicationPackageName>>({ it.first }, { it.second.value }))
            .forEach { (_, eventsAtInstant) ->
                val firstEvent = eventsAtInstant.first()
                val elapsedRealtime = elapsedRealtimeFor(
                    occurredAt = firstEvent.occurredAt,
                    clockSnapshot = clockSnapshot
                ) ?: return@forEach
                val activities = activeActivities.getOrPut(firstEvent.packageName, ::mutableSetOf)
                val wasForeground = activities.isNotEmpty()

                eventsAtInstant.forEach { event ->
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
                when {
                    !wasForeground && isForeground -> emittedEvents +=
                        SharedSessionEvent.ApplicationForegrounded(
                            packageName = firstEvent.packageName,
                            observedAt = firstEvent.occurredAt,
                            elapsedRealtime = elapsedRealtime
                        )

                    wasForeground && !isForeground -> emittedEvents +=
                        SharedSessionEvent.ApplicationBackgrounded(
                            packageName = firstEvent.packageName,
                            observedAt = firstEvent.occurredAt,
                            elapsedRealtime = elapsedRealtime
                        )
                }

                if (!isForeground) activeActivities.remove(firstEvent.packageName)
            }

        return emittedEvents
    }

    private fun elapsedRealtimeFor(
        occurredAt: Instant,
        clockSnapshot: ReconciliationClockSnapshot
    ): Duration? {
        val eventAge = Duration.between(occurredAt, clockSnapshot.observedAt)
        if (eventAge.isNegative || eventAge > clockSnapshot.elapsedRealtime) return null
        return clockSnapshot.elapsedRealtime.minus(eventAge)
    }

    private data class TrackedUsageStatsEvent(
        val key: UsageStatsEventKey,
        val packageName: ApplicationPackageName,
        val activityClassName: String?,
        val eventType: UsageStatsActivityEventType,
        val occurredAt: Instant
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
            fun from(record: UsageStatsEventRecord): UsageStatsEventKey =
                UsageStatsEventKey(
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
