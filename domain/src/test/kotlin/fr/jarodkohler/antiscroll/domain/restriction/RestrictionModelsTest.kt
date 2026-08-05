package fr.jarodkohler.antiscroll.domain.restriction

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RestrictionModelsTest {
    @Test
    fun profileAndPolicyRetainExplicitConfiguration() {
        val policy = SharedSessionPolicy(
            maximumDuration = Duration.ofMinutes(12),
            inactivityTimeout = Duration.ofMinutes(3)
        )
        val profile = RestrictionProfile(
            identifier = RestrictionProfileIdentifier("normal"),
            version = 2,
            sharedSessionPolicy = policy
        )

        assertEquals(policy, profile.sharedSessionPolicy)
        assertEquals(2, profile.version)
    }

    @Test
    fun sharedSessionMaximumDurationMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) {
            SharedSessionPolicy(
                maximumDuration = Duration.ZERO,
                inactivityTimeout = Duration.ofMinutes(2)
            )
        }
    }

    @Test
    fun sharedSessionInactivityTimeoutMustNotBeNegative() {
        assertThrows(IllegalArgumentException::class.java) {
            SharedSessionPolicy(
                maximumDuration = Duration.ofMinutes(10),
                inactivityTimeout = Duration.ofSeconds(-1)
            )
        }
    }

    @Test
    fun profileIdentifierAndVersionMustBeValid() {
        assertThrows(IllegalArgumentException::class.java) {
            RestrictionProfileIdentifier(" ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestrictionProfile(
                identifier = RestrictionProfileIdentifier("normal"),
                version = 0,
                sharedSessionPolicy = null
            )
        }
    }
}
