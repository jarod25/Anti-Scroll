package fr.jarodkohler.antiscroll.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shared_session_state")
data class SharedSessionStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Int,
    @ColumnInfo(name = "profile_identifier")
    val profileIdentifier: String,
    @ColumnInfo(name = "profile_version")
    val profileVersion: Int,
    @ColumnInfo(name = "boot_identifier")
    val bootIdentifier: Int,
    @ColumnInfo(name = "started_at_epoch_millis")
    val startedAtEpochMillis: Long,
    @ColumnInfo(name = "started_at_elapsed_realtime_millis")
    val startedAtElapsedRealtimeMillis: Long,
    @ColumnInfo(name = "accumulated_foreground_duration_millis")
    val accumulatedForegroundDurationMillis: Long,
    @ColumnInfo(name = "foreground_package_name")
    val foregroundPackageName: String?,
    @ColumnInfo(name = "foreground_since_elapsed_realtime_millis")
    val foregroundSinceElapsedRealtimeMillis: Long?,
    @ColumnInfo(name = "inactive_since_elapsed_realtime_millis")
    val inactiveSinceElapsedRealtimeMillis: Long?,
    @ColumnInfo(name = "last_observed_at_epoch_millis")
    val lastObservedAtEpochMillis: Long,
    @ColumnInfo(name = "last_observed_elapsed_realtime_millis")
    val lastObservedElapsedRealtimeMillis: Long
)
