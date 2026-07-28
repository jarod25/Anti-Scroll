package fr.jarodkohler.antiscroll.observation

import fr.jarodkohler.antiscroll.domain.observation.UsageEventSource
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

    @Test
    fun defaultSessionProfileMergesOnlyShortInternalTransitions() {
        val policy = DefaultObservationProfile.sessionReconstructionPolicy

        assertEquals(UsageEventSource.USAGE_STATS, policy.source)
        assertEquals(Duration.ofSeconds(3), policy.internalTransitionGrace)
        assertEquals(Duration.ofHours(6), policy.boundaryLookback)
    }

    @Test
    fun defaultBaselineRequiresAFullReliableWeek() {
        assertEquals(7, DefaultObservationProfile.baselinePolicy.requiredReliableDays)
    }
}
