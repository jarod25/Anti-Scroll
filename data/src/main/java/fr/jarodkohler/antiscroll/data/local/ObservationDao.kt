package fr.jarodkohler.antiscroll.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ObservationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvents(events: List<UsageEventEntity>): List<Long>

    @Query(
        """
        SELECT * FROM usage_events
        WHERE occurred_at_epoch_millis >= :startInclusiveEpochMillis
          AND occurred_at_epoch_millis < :endExclusiveEpochMillis
          AND package_name IN (:packageNames)
        ORDER BY occurred_at_epoch_millis, package_name, event_type, event_id
        """
    )
    suspend fun findEventsIn(
        startInclusiveEpochMillis: Long,
        endExclusiveEpochMillis: Long,
        packageNames: Set<String>
    ): List<UsageEventEntity>

    @Query(
        """
        SELECT * FROM usage_events
        WHERE package_name = :packageName
          AND source = :source
          AND occurred_at_epoch_millis < :beforeExclusiveEpochMillis
        ORDER BY occurred_at_epoch_millis DESC, event_id DESC
        LIMIT 1
        """
    )
    suspend fun findLatestEventBefore(
        packageName: String,
        source: String,
        beforeExclusiveEpochMillis: Long
    ): UsageEventEntity?

    @Query("SELECT * FROM collection_checkpoints WHERE source = :source LIMIT 1")
    suspend fun findCheckpoint(source: String): CollectionCheckpointEntity?

    @Upsert
    suspend fun upsertCheckpoint(checkpoint: CollectionCheckpointEntity)

    @Query(
        """
        SELECT * FROM collection_gaps
        WHERE start_inclusive_epoch_millis < :endExclusiveEpochMillis
          AND (end_exclusive_epoch_millis IS NULL OR end_exclusive_epoch_millis > :startInclusiveEpochMillis)
        ORDER BY start_inclusive_epoch_millis, gap_id
        """
    )
    fun observeGaps(startInclusiveEpochMillis: Long, endExclusiveEpochMillis: Long): Flow<List<CollectionGapEntity>>

    @Query(
        """
        SELECT * FROM collection_gaps
        WHERE source = :source
          AND start_inclusive_epoch_millis < :endExclusiveEpochMillis
          AND (end_exclusive_epoch_millis IS NULL OR end_exclusive_epoch_millis > :startInclusiveEpochMillis)
        ORDER BY start_inclusive_epoch_millis, gap_id
        """
    )
    suspend fun findGapsIn(
        source: String,
        startInclusiveEpochMillis: Long,
        endExclusiveEpochMillis: Long
    ): List<CollectionGapEntity>

    @Insert
    suspend fun insertGap(gap: CollectionGapEntity)

    @Query("SELECT * FROM monitoring_health WHERE singleton_id = 1 LIMIT 1")
    fun observeHealth(): Flow<MonitoringHealthEntity?>

    @Query("SELECT * FROM monitoring_health WHERE singleton_id = 1 LIMIT 1")
    suspend fun findHealth(): MonitoringHealthEntity?

    @Upsert
    suspend fun upsertHealth(health: MonitoringHealthEntity)
}
