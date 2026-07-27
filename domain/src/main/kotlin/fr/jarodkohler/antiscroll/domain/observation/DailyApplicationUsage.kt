package fr.jarodkohler.antiscroll.domain.observation

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import java.time.Duration
import java.time.LocalDate

data class DailyApplicationUsage(
    val date: LocalDate,
    val packageName: ApplicationPackageName,
    val foregroundDuration: Duration,
    val estimatedOpeningCount: Int,
    val completeness: DataCompleteness
) {
    init {
        require(!foregroundDuration.isNegative) {
            "Daily foreground duration must not be negative"
        }
        require(estimatedOpeningCount >= 0) {
            "Estimated opening count must not be negative"
        }
    }
}
