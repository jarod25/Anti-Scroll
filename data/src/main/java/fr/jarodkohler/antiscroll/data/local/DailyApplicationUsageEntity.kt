package fr.jarodkohler.antiscroll.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "daily_application_usage",
    primaryKeys = ["date_epoch_day", "package_name"],
    indices = [Index(value = ["package_name", "date_epoch_day"])]
)
data class DailyApplicationUsageEntity(
    @ColumnInfo(name = "date_epoch_day")
    val dateEpochDay: Long,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "foreground_duration_millis")
    val foregroundDurationMillis: Long,
    @ColumnInfo(name = "estimated_opening_count")
    val estimatedOpeningCount: Int,
    @ColumnInfo(name = "completeness")
    val completeness: String
)
