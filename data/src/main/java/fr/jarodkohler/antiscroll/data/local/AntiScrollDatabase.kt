package fr.jarodkohler.antiscroll.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

object AntiScrollDatabaseSchema {
    const val VERSION = 1
    const val NAME = "anti_scroll.db"
}

@Database(
    entities = [MonitoredApplicationEntity::class],
    version = AntiScrollDatabaseSchema.VERSION,
    exportSchema = true,
)
abstract class AntiScrollDatabase : RoomDatabase() {
    abstract fun monitoredApplicationDao(): MonitoredApplicationDao
}
