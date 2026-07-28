package fr.jarodkohler.antiscroll.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DailyUsageDao {
    @Query(
        "SELECT * FROM daily_application_usage " +
            "WHERE date_epoch_day = :dateEpochDay ORDER BY package_name"
    )
    abstract fun observe(dateEpochDay: Long): Flow<List<DailyApplicationUsageEntity>>

    @Query("DELETE FROM daily_application_usage WHERE date_epoch_day = :dateEpochDay")
    protected abstract suspend fun deleteForDate(dateEpochDay: Long)

    @Upsert
    protected abstract suspend fun upsertAll(usage: List<DailyApplicationUsageEntity>)

    @Transaction
    open suspend fun replaceForDate(
        dateEpochDay: Long,
        usage: List<DailyApplicationUsageEntity>
    ) {
        deleteForDate(dateEpochDay)
        if (usage.isNotEmpty()) {
            upsertAll(usage)
        }
    }
}
