package fr.jarodkohler.antiscroll.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonitoredApplicationDaoInstrumentedTest {
    private lateinit var database: AntiScrollDatabase
    private lateinit var dao: MonitoredApplicationDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AntiScrollDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.monitoredApplicationDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun upsertPersistsAndUpdatesApplicationConfiguration() = runBlocking {
        val initial = MonitoredApplicationEntity(
            packageName = "com.example.scroll",
            isEnabled = true,
            addedAtEpochMillis = 1_000L,
        )
        dao.upsert(initial)

        assertEquals(initial, dao.findByPackageName(initial.packageName))
        assertEquals(listOf(initial), dao.observeAll().first())

        val updated = initial.copy(isEnabled = false)
        dao.upsert(updated)

        assertEquals(updated, dao.findByPackageName(initial.packageName))
        assertEquals(listOf(updated), dao.observeAll().first())
    }

    @Test
    fun deleteRemovesApplicationConfiguration() = runBlocking {
        val application = MonitoredApplicationEntity(
            packageName = "com.example.scroll",
            isEnabled = true,
            addedAtEpochMillis = 1_000L,
        )
        dao.upsert(application)

        dao.deleteByPackageName(application.packageName)

        assertNull(dao.findByPackageName(application.packageName))
        assertEquals(emptyList<MonitoredApplicationEntity>(), dao.observeAll().first())
    }
}
