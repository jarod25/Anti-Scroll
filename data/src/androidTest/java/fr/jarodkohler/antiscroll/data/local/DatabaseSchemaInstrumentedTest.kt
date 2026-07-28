package fr.jarodkohler.antiscroll.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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
        migrationHelper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                "INSERT INTO monitored_applications " +
                    "(package_name, is_enabled, added_at_epoch_millis) " +
                    "VALUES ('com.instagram.android', 1, 1000)"
            )
            close()
        }

        val database = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
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

    private companion object {
        const val TEST_DATABASE = "anti-scroll-migration-test"
    }
}
