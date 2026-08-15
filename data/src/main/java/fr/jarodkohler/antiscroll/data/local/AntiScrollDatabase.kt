package fr.jarodkohler.antiscroll.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

object AntiScrollDatabaseSchema {
    const val VERSION = 4
    const val NAME = "anti_scroll.db"
}

@Database(
    entities = [
        MonitoredApplicationEntity::class,
        UsageEventEntity::class,
        DailyApplicationUsageEntity::class,
        CollectionCheckpointEntity::class,
        CollectionGapEntity::class,
        MonitoringHealthEntity::class,
        SharedSessionStateEntity::class
    ],
    version = AntiScrollDatabaseSchema.VERSION,
    exportSchema = true
)
abstract class AntiScrollDatabase : RoomDatabase() {
    abstract fun monitoredApplicationDao(): MonitoredApplicationDao

    abstract fun observationDao(): ObservationDao

    abstract fun dailyUsageDao(): DailyUsageDao

    abstract fun sharedSessionStateDao(): SharedSessionStateDao
}
