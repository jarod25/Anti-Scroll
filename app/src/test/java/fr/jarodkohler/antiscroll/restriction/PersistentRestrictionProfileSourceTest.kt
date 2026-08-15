package fr.jarodkohler.antiscroll.restriction

import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfile
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfileIdentifier
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfileSelectionRepository
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionPolicy
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentRestrictionProfileSourceTest {
    private val observationProfile = RestrictionProfile(
        identifier = RestrictionProfileIdentifier("observation"),
        version = 1,
        sharedSessionPolicy = null
    )
    private val normalProfile = RestrictionProfile(
        identifier = RestrictionProfileIdentifier("normal"),
        version = 1,
        sharedSessionPolicy = SharedSessionPolicy(
            maximumDuration = Duration.ofMinutes(10),
            inactivityTimeout = Duration.ofMinutes(2)
        )
    )
    private val catalog = FakeRestrictionProfileCatalog(
        defaultProfile = observationProfile,
        profiles = listOf(observationProfile, normalProfile)
    )

    @Test
    fun initializePersistsDefaultProfileWhenNoSelectionExists() = runTest {
        val repository = FakeRestrictionProfileSelectionRepository()
        val source = PersistentRestrictionProfileSource(repository, catalog)

        source.initialize()

        assertEquals(observationProfile, source.activeProfile.value)
        assertEquals(observationProfile.identifier, repository.identifier)
    }

    @Test
    fun initializeRestoresPersistedProfileBeforePublishingIt() = runTest {
        val repository = FakeRestrictionProfileSelectionRepository(normalProfile.identifier)
        val source = PersistentRestrictionProfileSource(repository, catalog)

        source.initialize()

        assertEquals(normalProfile, source.activeProfile.value)
        assertEquals(normalProfile.identifier, repository.identifier)
    }

    @Test
    fun activatePersistsSelectionBeforePublishingProfile() = runTest {
        lateinit var source: PersistentRestrictionProfileSource
        val repository = FakeRestrictionProfileSelectionRepository(
            identifier = observationProfile.identifier,
            onSave = { identifier ->
                assertEquals(observationProfile, source.activeProfile.value)
                assertEquals(normalProfile.identifier, identifier)
            }
        )
        source = PersistentRestrictionProfileSource(repository, catalog)
        source.initialize()

        source.activate(normalProfile.identifier)

        assertEquals(normalProfile, source.activeProfile.value)
        assertEquals(normalProfile.identifier, repository.identifier)
    }

    @Test
    fun unknownPersistedProfileFailsInsteadOfFallingBackToObservation() = runTest {
        val repository = FakeRestrictionProfileSelectionRepository(
            RestrictionProfileIdentifier("missing")
        )
        val source = PersistentRestrictionProfileSource(repository, catalog)
        var failedAsExpected = false

        try {
            source.initialize()
        } catch (_: IllegalStateException) {
            failedAsExpected = true
        }

        assertTrue(failedAsExpected)
        assertEquals(observationProfile, source.activeProfile.value)
    }

    private class FakeRestrictionProfileCatalog(
        override val defaultProfile: RestrictionProfile,
        private val profiles: List<RestrictionProfile>
    ) : RestrictionProfileCatalog {
        override fun resolve(identifier: RestrictionProfileIdentifier): RestrictionProfile? =
            profiles.firstOrNull { profile -> profile.identifier == identifier }
    }

    private class FakeRestrictionProfileSelectionRepository(
        var identifier: RestrictionProfileIdentifier? = null,
        private val onSave: (RestrictionProfileIdentifier) -> Unit = {}
    ) : RestrictionProfileSelectionRepository {
        override suspend fun load(): RestrictionProfileIdentifier? = identifier

        override suspend fun save(identifier: RestrictionProfileIdentifier) {
            onSave(identifier)
            this.identifier = identifier
        }
    }
}
