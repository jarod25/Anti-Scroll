package fr.jarodkohler.antiscroll.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoredApplicationDao {
    @Query("SELECT * FROM monitored_applications ORDER BY package_name")
    fun observeAll(): Flow<List<MonitoredApplicationEntity>>

    @Query("SELECT * FROM monitored_applications WHERE package_name = :packageName LIMIT 1")
    suspend fun findByPackageName(packageName: String): MonitoredApplicationEntity?

    @Upsert
    suspend fun upsert(application: MonitoredApplicationEntity)

    @Query("DELETE FROM monitored_applications WHERE package_name = :packageName")
    suspend fun deleteByPackageName(packageName: String)
}
