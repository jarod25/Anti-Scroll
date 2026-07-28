package fr.jarodkohler.antiscroll.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.jarodkohler.antiscroll.data.local.AntiScrollDatabase
import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.application.MonitoredApplication
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMonitoredApplicationRepositoryInstrumentedTest {
    private lateinit var database: AntiScrollDatabase
    private lateinit var repository: RoomMonitoredApplicationRepository

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AntiScrollDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = RoomMonitoredApplicationRepository(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun repositoryMapsAndObservesSavedApplications() = runBlocking {
        val application = MonitoredApplication(
            packageName = ApplicationPackageName("com.instagram.android"),
            isEnabled = true,
            addedAt = Instant.ofEpochMilli(1_000L)
        )

        repository.save(application)

        assertEquals(listOf(application), repository.observeAll().first())
        assertEquals(listOf(application), repository.enabledApplications())
    }

    @Test
    fun disabledApplicationsRemainStoredButAreExcludedFromEnabledQuery() = runBlocking {
        val application = MonitoredApplication(
            packageName = ApplicationPackageName("com.reddit.frontpage"),
            isEnabled = false,
            addedAt = Instant.ofEpochMilli(2_000L)
        )

        repository.save(application)

        assertEquals(listOf(application), repository.observeAll().first())
        assertEquals(emptyList<MonitoredApplication>(), repository.enabledApplications())
    }
}
