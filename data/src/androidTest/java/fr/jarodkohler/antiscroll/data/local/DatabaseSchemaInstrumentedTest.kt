package fr.jarodkohler.antiscroll.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseSchemaInstrumentedTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AntiScrollDatabase::class.java
    )

    @Test
    fun migrationOneToTwoPreservesConfigurationAndCreatesObservationTables() {
        migrationHelper.createDatabase(VERSION_ONE_DATABASE, 1).apply {
            execSQL(
                "INSERT INTO monitored_applications " +
                    "(package_name, is_enabled, added_at_epoch_millis) " +
                    "VALUES ('com.instagram.android', 1, 1000)"
            )
            close()
        }

        val database = migrationHelper.runMigrationsAndValidate(
            VERSION_ONE_DATABASE,
            2,
            true,
            DatabaseMigrations.MIGRATION_1_2
        )

        try {
            database.query(
                "SELECT is_enabled, added_at_epoch_millis FROM monitored_applications " +
                    "WHERE package_name = 'com.instagram.android'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
                assertEquals(1_000L, cursor.getLong(1))
            }

            val expectedTables = setOf(
                "usage_events",
                "daily_application_usage",
                "collection_checkpoints",
                "collection_gaps",
                "monitoring_health"
            )
            database.query(
                "SELECT name FROM sqlite_master WHERE type = 'table'"
            ).use { cursor ->
                val actualTables = buildSet {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(0))
                    }
                }
                assertTrue(actualTables.containsAll(expectedTables))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun migrationTwoToThreePreservesEventsAndAddsNullableActivityClass() {
        migrationHelper.createDatabase(VERSION_TWO_DATABASE, 2).apply {
            execSQL(
                "INSERT INTO usage_events " +
                    "(event_id, package_name, event_type, occurred_at_epoch_millis, source, reliability) " +
                    "VALUES ('event-1', 'com.zhiliaoapp.musically', 'FOREGROUND_ENTERED', " +
                    "1000, 'USAGE_STATS', 'OBSERVED')"
            )
            close()
        }

        val database = migrationHelper.runMigrationsAndValidate(
            VERSION_TWO_DATABASE,
            3,
            true,
            DatabaseMigrations.MIGRATION_2_3
        )

        try {
            database.query(
                "SELECT package_name, activity_class_name FROM usage_events WHERE event_id = 'event-1'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("com.zhiliaoapp.musically", cursor.getString(0))
                assertNull(cursor.getString(1))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun migrationThreeToFourPreservesObservationDataAndCreatesSharedSessionState() {
        migrationHelper.createDatabase(VERSION_THREE_DATABASE, 3).apply {
            execSQL(
                "INSERT INTO usage_events " +
                    "(event_id, package_name, event_type, occurred_at_epoch_millis, source, reliability, " +
                    "activity_class_name) VALUES ('event-2', 'com.instagram.android', 'FOREGROUND_ENTERED', " +
                    "2000, 'USAGE_STATS', 'OBSERVED', 'MainActivity')"
            )
            close()
        }

        val database = migrationHelper.runMigrationsAndValidate(
            VERSION_THREE_DATABASE,
            4,
            true,
            DatabaseMigrations.MIGRATION_3_4
        )

        try {
            database.query(
                "SELECT package_name, activity_class_name FROM usage_events WHERE event_id = 'event-2'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("com.instagram.android", cursor.getString(0))
                assertEquals("MainActivity", cursor.getString(1))
            }
            database.query("SELECT * FROM shared_session_state").use { cursor ->
                assertFalse(cursor.moveToFirst())
            }
        } finally {
            database.close()
        }
    }

    private companion object {
        const val VERSION_ONE_DATABASE = "anti-scroll-migration-v1-test"
        const val VERSION_TWO_DATABASE = "anti-scroll-migration-v2-test"
        const val VERSION_THREE_DATABASE = "anti-scroll-migration-v3-test"
    }
}
