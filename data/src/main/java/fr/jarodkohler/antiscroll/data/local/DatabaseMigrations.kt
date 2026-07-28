package fr.jarodkohler.antiscroll.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `usage_events` (
                    `event_id` TEXT NOT NULL,
                    `package_name` TEXT NOT NULL,
                    `event_type` TEXT NOT NULL,
                    `occurred_at_epoch_millis` INTEGER NOT NULL,
                    `source` TEXT NOT NULL,
                    `reliability` TEXT NOT NULL,
                    PRIMARY KEY(`event_id`)
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_usage_events_occurred_at_epoch_millis` " +
                    "ON `usage_events` (`occurred_at_epoch_millis`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_usage_events_package_name_occurred_at_epoch_millis` " +
                    "ON `usage_events` (`package_name`, `occurred_at_epoch_millis`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_usage_events_source_occurred_at_epoch_millis` " +
                    "ON `usage_events` (`source`, `occurred_at_epoch_millis`)"
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `daily_application_usage` (
                    `date_epoch_day` INTEGER NOT NULL,
                    `package_name` TEXT NOT NULL,
                    `foreground_duration_millis` INTEGER NOT NULL,
                    `estimated_opening_count` INTEGER NOT NULL,
                    `completeness` TEXT NOT NULL,
                    PRIMARY KEY(`date_epoch_day`, `package_name`)
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_daily_application_usage_package_name_date_epoch_day` " +
                    "ON `daily_application_usage` (`package_name`, `date_epoch_day`)"
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `collection_checkpoints` (
                    `source` TEXT NOT NULL,
                    `reconciled_through_epoch_millis` INTEGER NOT NULL,
                    `updated_at_epoch_millis` INTEGER NOT NULL,
                    PRIMARY KEY(`source`)
                )
                """.trimIndent()
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `collection_gaps` (
                    `gap_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `source` TEXT NOT NULL,
                    `start_inclusive_epoch_millis` INTEGER NOT NULL,
                    `end_exclusive_epoch_millis` INTEGER,
                    `reason` TEXT NOT NULL,
                    `detected_at_epoch_millis` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_collection_gaps_start_inclusive_epoch_millis` " +
                    "ON `collection_gaps` (`start_inclusive_epoch_millis`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_collection_gaps_source_start_inclusive_epoch_millis` " +
                    "ON `collection_gaps` (`source`, `start_inclusive_epoch_millis`)"
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `monitoring_health` (
                    `singleton_id` INTEGER NOT NULL,
                    `usage_access_status` TEXT NOT NULL,
                    `accessibility_status` TEXT NOT NULL,
                    `collection_status` TEXT NOT NULL,
                    `last_successful_reconciliation_epoch_millis` INTEGER,
                    PRIMARY KEY(`singleton_id`)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE `usage_events` ADD COLUMN `activity_class_name` TEXT"
            )
        }
    }
}
