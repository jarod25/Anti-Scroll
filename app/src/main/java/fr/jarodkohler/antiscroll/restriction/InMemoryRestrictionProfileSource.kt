package fr.jarodkohler.antiscroll.restriction

import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfile
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfileSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class InMemoryRestrictionProfileSource @Inject constructor() : RestrictionProfileSource {
    private val mutableActiveProfile = MutableStateFlow(DefaultRestrictionProfiles.observation)

    override val activeProfile: StateFlow<RestrictionProfile> = mutableActiveProfile.asStateFlow()
}
