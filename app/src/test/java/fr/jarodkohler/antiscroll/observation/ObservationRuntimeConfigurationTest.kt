package fr.jarodkohler.antiscroll.observation

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationRuntimeConfigurationTest {
    @Test
    fun defaultSchedulingProfileLimitsBackgroundBatteryImpact() {
        val policy = DefaultObservationProfile.schedulingPolicy

        assertEquals(Duration.ofHours(1), policy.periodicInterval)
        assertTrue(policy.requiresBatteryNotLow)
    }
}
