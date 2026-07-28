package fr.jarodkohler.antiscroll.domain.observation

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import java.time.Duration
import java.time.LocalDate

enum class ObservationBaselineStatus {
    NOT_STARTED,
    COLLECTING,
    READY
}

data class ApplicationUsageBaseline(
    val packageName: ApplicationPackageName,
    val typicalForegroundDuration: Duration,
    val typicalOpeningCount: Int
) {
    init {
        require(!typicalForegroundDuration.isNegative) {
            "Application baseline duration must not be negative"
        }
        require(typicalOpeningCount >= 0) {
            "Application baseline opening count must not be negative"
        }
    }
}

data class ObservationBaseline(
    val status: ObservationBaselineStatus,
    val requiredReliableDays: Int,
    val reliableDayCount: Int,
    val observationStartedOn: LocalDate? = null,
    val baselineCompletedOn: LocalDate? = null,
    val typicalGlobalForegroundDuration: Duration? = null,
    val typicalGlobalOpeningCount: Int? = null,
    val applications: List<ApplicationUsageBaseline> = emptyList()
) {
    init {
        require(requiredReliableDays > 0) { "Required baseline day count must be positive" }
        require(reliableDayCount in 0..requiredReliableDays) {
            "Reliable baseline day count must be within the configured target"
        }
        require(applications.map(ApplicationUsageBaseline::packageName).distinct().size == applications.size) {
            "Application baselines must contain unique package names"
        }

        if (status == ObservationBaselineStatus.READY) {
            require(reliableDayCount == requiredReliableDays) {
                "A ready baseline must contain the configured number of reliable days"
            }
            require(
                observationStartedOn != null &&
                    baselineCompletedOn != null &&
                    typicalGlobalForegroundDuration != null &&
                    typicalGlobalOpeningCount != null &&
                    applications.isNotEmpty()
            ) {
                "A ready baseline must expose its period and typical usage values"
            }
        }
    }

    val remainingReliableDays: Int
        get() = requiredReliableDays - reliableDayCount
}
