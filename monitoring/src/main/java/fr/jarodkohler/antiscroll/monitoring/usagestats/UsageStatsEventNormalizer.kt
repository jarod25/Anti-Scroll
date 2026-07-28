package fr.jarodkohler.antiscroll.monitoring.usagestats

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.observation.NormalizedUsageEvent
import fr.jarodkohler.antiscroll.domain.observation.ObservationWindow
import fr.jarodkohler.antiscroll.domain.observation.UsageEventId
import fr.jarodkohler.antiscroll.domain.observation.UsageEventReliability
import fr.jarodkohler.antiscroll.domain.observation.UsageEventSource
import fr.jarodkohler.antiscroll.domain.observation.UsageEventType
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject

class UsageStatsEventNormalizer @Inject constructor() {
    fun normalize(
        records: Collection<UsageStatsEventRecord>,
        window: ObservationWindow,
        packageNames: Set<ApplicationPackageName>
    ): List<NormalizedUsageEvent> {
        if (records.isEmpty() || packageNames.isEmpty()) return emptyList()

        val allowedPackages = packageNames.associateBy(ApplicationPackageName::value)

        return records.mapNotNull { record ->
            val packageName = allowedPackages[record.packageName] ?: return@mapNotNull null
            val occurredAt = Instant.ofEpochMilli(record.occurredAtEpochMillis)
            if (occurredAt !in window) return@mapNotNull null

            NormalizedUsageEvent(
                id = deterministicId(record),
                packageName = packageName,
                type = record.eventType.toDomainType(),
                occurredAt = occurredAt,
                source = UsageEventSource.USAGE_STATS,
                reliability = UsageEventReliability.OBSERVED
            )
        }.sortedWith(
            compareBy<NormalizedUsageEvent>(NormalizedUsageEvent::occurredAt)
                .thenBy { event -> event.packageName.value }
                .thenBy { event -> event.type.ordinal }
                .thenBy { event -> event.id.value }
        ).distinctBy(NormalizedUsageEvent::id)
    }

    private fun deterministicId(record: UsageStatsEventRecord): UsageEventId {
        val canonicalValue = listOf(
            UsageEventSource.USAGE_STATS.name,
            record.packageName,
            record.eventType.name,
            record.occurredAtEpochMillis.toString(),
            record.activityClassName.orEmpty(),
            record.instanceId.toString()
        ).joinToString(separator = "|")

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonicalValue.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

        return UsageEventId("usage-stats:$digest")
    }
}

private fun UsageStatsActivityEventType.toDomainType(): UsageEventType =
    when (this) {
        UsageStatsActivityEventType.RESUMED -> UsageEventType.FOREGROUND_ENTERED
        UsageStatsActivityEventType.PAUSED -> UsageEventType.FOREGROUND_EXITED
    }
