package fr.jarodkohler.antiscroll.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "usage_events",
    indices = [
        Index(value = ["occurred_at_epoch_millis"]),
        Index(value = ["package_name", "occurred_at_epoch_millis"]),
        Index(value = ["source", "occurred_at_epoch_millis"])
    ]
)
data class UsageEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id")
    val eventId: String,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "event_type")
    val eventType: String,
    @ColumnInfo(name = "occurred_at_epoch_millis")
    val occurredAtEpochMillis: Long,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "reliability")
    val reliability: String
)
