package fr.jarodkohler.antiscroll.data.repository

import fr.jarodkohler.antiscroll.data.local.AntiScrollDatabase
import fr.jarodkohler.antiscroll.data.local.DailyApplicationUsageEntity
import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.observation.DailyApplicationUsage
import fr.jarodkohler.antiscroll.domain.observation.DailyUsageRepository
import fr.jarodkohler.antiscroll.domain.observation.DataCompleteness
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomDailyUsageRepository @Inject constructor(database: AntiScrollDatabase) : DailyUsageRepository {
    private val dao = database.dailyUsageDao()

    override fun observe(date: LocalDate): Flow<List<DailyApplicationUsage>> =
        dao.observe(date.toEpochDay()).map { entities ->
            entities.map(DailyApplicationUsageEntity::toDomain)
        }

    override fun observeRange(fromInclusive: LocalDate, toInclusive: LocalDate): Flow<List<DailyApplicationUsage>> {
        require(!toInclusive.isBefore(fromInclusive)) {
            "Daily usage range must not end before it starts"
        }
        return dao.observeRange(
            fromEpochDay = fromInclusive.toEpochDay(),
            toEpochDay = toInclusive.toEpochDay()
        ).map { entities ->
            entities.map(DailyApplicationUsageEntity::toDomain)
        }
    }

    override suspend fun replace(date: LocalDate, usage: List<DailyApplicationUsage>) {
        require(usage.all { item -> item.date == date }) {
            "Daily usage replacement must contain only the requested date"
        }
        require(usage.map(DailyApplicationUsage::packageName).distinct().size == usage.size) {
            "Daily usage replacement must contain unique package names"
        }

        dao.replaceForDate(
            dateEpochDay = date.toEpochDay(),
            usage = usage.map(DailyApplicationUsage::toEntity)
        )
    }
}

private fun DailyApplicationUsage.toEntity(): DailyApplicationUsageEntity = DailyApplicationUsageEntity(
    dateEpochDay = date.toEpochDay(),
    packageName = packageName.value,
    foregroundDurationMillis = foregroundDuration.toMillis(),
    estimatedOpeningCount = estimatedOpeningCount,
    completeness = completeness.name
)

private fun DailyApplicationUsageEntity.toDomain(): DailyApplicationUsage = DailyApplicationUsage(
    date = LocalDate.ofEpochDay(dateEpochDay),
    packageName = ApplicationPackageName(packageName),
    foregroundDuration = Duration.ofMillis(foregroundDurationMillis),
    estimatedOpeningCount = estimatedOpeningCount,
    completeness = DataCompleteness.valueOf(completeness)
)
