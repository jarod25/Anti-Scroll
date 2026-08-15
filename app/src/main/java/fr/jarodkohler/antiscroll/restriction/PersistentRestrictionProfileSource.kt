package fr.jarodkohler.antiscroll.restriction

import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfile
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfileController
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfileIdentifier
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfileSelectionRepository
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfileSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class PersistentRestrictionProfileSource
@Inject
constructor(
    private val repository: RestrictionProfileSelectionRepository,
    private val catalog: RestrictionProfileCatalog
) : RestrictionProfileSource, RestrictionProfileController {
    private val initializationMutex = Mutex()
    private val mutableActiveProfile = MutableStateFlow(catalog.defaultProfile)
    private var initialized = false

    override val activeProfile: StateFlow<RestrictionProfile> = mutableActiveProfile.asStateFlow()

    override suspend fun initialize() {
        initializationMutex.withLock {
            if (initialized) return

            val persistedIdentifier = repository.load()
            val profile = if (persistedIdentifier == null) {
                catalog.defaultProfile.also { defaultProfile ->
                    repository.save(defaultProfile.identifier)
                }
            } else {
                checkNotNull(catalog.resolve(persistedIdentifier)) {
                    "Persisted restriction profile '$persistedIdentifier' is not available"
                }
            }

            mutableActiveProfile.value = profile
            initialized = true
        }
    }

    override suspend fun activate(identifier: RestrictionProfileIdentifier) {
        initializationMutex.withLock {
            val profile = checkNotNull(catalog.resolve(identifier)) {
                "Restriction profile '$identifier' is not available"
            }

            repository.save(identifier)
            mutableActiveProfile.value = profile
            initialized = true
        }
    }
}
