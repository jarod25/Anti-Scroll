package fr.jarodkohler.antiscroll.monitoring.usagestats

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageStatsQueryModeTest {
    @Test
    fun api35AndAboveUseFilteredQueries() {
        assertEquals(UsageStatsQueryMode.FILTERED, usageStatsQueryModeForApi(35))
        assertEquals(UsageStatsQueryMode.FILTERED, usageStatsQueryModeForApi(36))
    }

    @Test
    fun olderSupportedApisUseLegacyQueries() {
        assertEquals(UsageStatsQueryMode.LEGACY, usageStatsQueryModeForApi(29))
        assertEquals(UsageStatsQueryMode.LEGACY, usageStatsQueryModeForApi(34))
    }
}
