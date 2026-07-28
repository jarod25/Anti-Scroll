package fr.jarodkohler.antiscroll.engine.observation

import fr.jarodkohler.antiscroll.domain.application.MonitoredApplication
import fr.jarodkohler.antiscroll.domain.observation.ApplicationUsageBaseline
import fr.jarodkohler.antiscroll.domain.observation.DailyApplicationUsage
import fr.jarodkohler.antiscroll.domain.observation.DataCompleteness
import fr.jarodkohler.antiscroll.domain.observation.ObservationBaseline
import fr.jarodkohler.antiscroll.domain.observation.ObservationBaselineStatus
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class ObservationBaselineCalculator(private val policy: ObservationBaselinePolicy) {
    fun calculate(
        monitoredApplications: List<MonitoredApplication>,
        dailyUsage: List<DailyApplicationUsage>,
        currentDate: LocalDate,
        zoneId: ZoneId
    ): ObservationBaseline {
        val enabledApplications = monitoredApplications
            .filter(MonitoredApplication::isEnabled)
            .sortedBy { application -> application.packageName.value }
        if (enabledApplications.isEmpty()) {
            return ObservationBaseline(
                status = ObservationBaselineStatus.NOT_STARTED,
                requiredReliableDays = policy.requiredReliableDays,
                reliableDayCount = 0
            )
        }

        val observationStartedOn = enabledApplications.maxOf { application ->
            application.addedAt.atZone(zoneId).toLocalDate()
        }
        val lastCompletedDate = currentDate.minusDays(1)
        if (lastCompletedDate.isBefore(observationStartedOn)) {
            return collectingBaseline(observationStartedOn, reliableDayCount = 0)
        }

        val enabledPackages = enabledApplications.map(MonitoredApplication::packageName).toSet()
        val usageByDate = dailyUsage
            .asSequence()
            .filter { usage -> usage.date in observationStartedOn..lastCompletedDate }
            .filter { usage -> usage.packageName in enabledPackages }
            .groupBy(DailyApplicationUsage::date)
            .mapValues { (_, usage) -> usage.associateBy(DailyApplicationUsage::packageName) }

        val reliableDates = usageByDate.keys
            .asSequence()
            .filter { date ->
                val usageForDate = usageByDate.getValue(date)
                enabledPackages.all { packageName ->
                    usageForDate[packageName]?.completeness == DataCompleteness.COMPLETE
                }
            }
            .sorted()
            .take(policy.requiredReliableDays)
            .toList()

        if (reliableDates.size < policy.requiredReliableDays) {
            return collectingBaseline(
                observationStartedOn = observationStartedOn,
                reliableDayCount = reliableDates.size
            )
        }

        val applicationBaselines = enabledApplications.map { application ->
            val usage = reliableDates.map { date ->
                usageByDate.getValue(date).getValue(application.packageName)
            }
            ApplicationUsageBaseline(
                packageName = application.packageName,
                typicalForegroundDuration = Duration.ofMillis(
                    medianLong(usage.map { item -> item.foregroundDuration.toMillis() })
                ),
                typicalOpeningCount = medianInt(usage.map(DailyApplicationUsage::estimatedOpeningCount))
            )
        }
        val globalDurationByDate = reliableDates.map { date ->
            usageByDate.getValue(date).values.sumOf { usage -> usage.foregroundDuration.toMillis() }
        }
        val globalOpeningsByDate = reliableDates.map { date ->
            usageByDate.getValue(date).values.sumOf(DailyApplicationUsage::estimatedOpeningCount)
        }

        return ObservationBaseline(
            status = ObservationBaselineStatus.READY,
            requiredReliableDays = policy.requiredReliableDays,
            reliableDayCount = policy.requiredReliableDays,
            observationStartedOn = observationStartedOn,
            baselineCompletedOn = reliableDates.last(),
            typicalGlobalForegroundDuration = Duration.ofMillis(medianLong(globalDurationByDate)),
            typicalGlobalOpeningCount = medianInt(globalOpeningsByDate),
            applications = applicationBaselines
        )
    }

    private fun collectingBaseline(
        observationStartedOn: LocalDate,
        reliableDayCount: Int
    ): ObservationBaseline = ObservationBaseline(
        status = ObservationBaselineStatus.COLLECTING,
        requiredReliableDays = policy.requiredReliableDays,
        reliableDayCount = reliableDayCount,
        observationStartedOn = observationStartedOn
    )
}

private fun medianLong(values: List<Long>): Long {
    require(values.isNotEmpty()) { "Median requires at least one value" }
    val sortedValues = values.sorted()
    val middle = sortedValues.size / 2
    return if (sortedValues.size % 2 == 1) {
        sortedValues[middle]
    } else {
        val lower = sortedValues[middle - 1]
        val upper = sortedValues[middle]
        lower + (upper - lower) / 2
    }
}

private fun medianInt(values: List<Int>): Int = medianLong(values.map(Int::toLong)).toInt()
