package fr.jarodkohler.antiscroll.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.jarodkohler.antiscroll.data.local.AntiScrollDatabase
import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.observation.DailyApplicationUsage
import fr.jarodkohler.antiscroll.domain.observation.DataCompleteness
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomDailyUsageRepositoryInstrumentedTest {
    private lateinit var database: AntiScrollDatabase
    private lateinit var repository: RoomDailyUsageRepository

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AntiScrollDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = RoomDailyUsageRepository(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun replaceOverwritesOnlyTheRequestedDay() = runBlocking {
        val firstDate = LocalDate.of(2026, 7, 28)
        val secondDate = firstDate.plusDays(1)
        val instagram = usage(firstDate, "com.instagram.android", 20_000L)
        val tiktok = usage(firstDate, "com.zhiliaoapp.musically", 30_000L)
        val nextDay = usage(secondDate, "com.instagram.android", 40_000L)

        repository.replace(firstDate, listOf(instagram))
        repository.replace(secondDate, listOf(nextDay))
        repository.replace(firstDate, listOf(tiktok))

        assertEquals(listOf(tiktok), repository.observe(firstDate).first())
        assertEquals(listOf(nextDay), repository.observe(secondDate).first())
    }

    @Test
    fun replaceRejectsMismatchedDatesAndDuplicatePackages() {
        val date = LocalDate.of(2026, 7, 28)
        val usage = usage(date, "com.instagram.android", 20_000L)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.replace(date, listOf(usage.copy(date = date.plusDays(1))))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.replace(date, listOf(usage, usage))
            }
        }
    }

    private fun usage(date: LocalDate, packageName: String, foregroundDurationMillis: Long): DailyApplicationUsage =
        DailyApplicationUsage(
            date = date,
            packageName = ApplicationPackageName(packageName),
            foregroundDuration = Duration.ofMillis(foregroundDurationMillis),
            estimatedOpeningCount = 2,
            completeness = DataCompleteness.COMPLETE
        )
}
