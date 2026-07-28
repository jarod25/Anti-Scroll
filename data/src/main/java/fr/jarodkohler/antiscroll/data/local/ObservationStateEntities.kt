package fr.jarodkohler.antiscroll.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "collection_checkpoints")
data class CollectionCheckpointEntity(
    @PrimaryKey
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "reconciled_through_epoch_millis")
    val reconciledThroughEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "collection_gaps",
    indices = [
        Index(value = ["start_inclusive_epoch_millis"]),
        Index(value = ["source", "start_inclusive_epoch_millis"])
    ]
)
data class CollectionGapEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "gap_id")
    val gapId: Long = 0,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "start_inclusive_epoch_millis")
    val startInclusiveEpochMillis: Long,
    @ColumnInfo(name = "end_exclusive_epoch_millis")
    val endExclusiveEpochMillis: Long?,
    @ColumnInfo(name = "reason")
    val reason: String,
    @ColumnInfo(name = "detected_at_epoch_millis")
    val detectedAtEpochMillis: Long
)

@Entity(tableName = "monitoring_health")
data class MonitoringHealthEntity(
    @PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Int,
    @ColumnInfo(name = "usage_access_status")
    val usageAccessStatus: String,
    @ColumnInfo(name = "accessibility_status")
    val accessibilityStatus: String,
    @ColumnInfo(name = "collection_status")
    val collectionStatus: String,
    @ColumnInfo(name = "last_successful_reconciliation_epoch_millis")
    val lastSuccessfulReconciliationEpochMillis: Long?
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
