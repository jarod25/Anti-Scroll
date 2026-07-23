package fr.jarodkohler.antiscroll.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseSchemaInstrumentedTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AntiScrollDatabase::class.java,
    )

    @Test
    fun exportedVersionOneSchemaCanBeCreated() {
        val database = migrationHelper.createDatabase(TEST_DATABASE, AntiScrollDatabaseSchema.VERSION)

        try {
            database.query(
                "SELECT name FROM sqlite_master " +
                    "WHERE type = 'table' AND name = 'monitored_applications'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
        } finally {
            database.close()
        }
    }

    private companion object {
        const val TEST_DATABASE = "anti-scroll-migration-test"
    }
}
