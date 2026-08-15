package fr.jarodkohler.antiscroll.restriction

import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfile
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfileIdentifier

object DefaultRestrictionProfiles : RestrictionProfileCatalog {
    val observation = RestrictionProfile(
        identifier = RestrictionProfileIdentifier("observation"),
        version = 1,
        sharedSessionPolicy = null
    )

    private val profiles = listOf(observation)

    override val defaultProfile: RestrictionProfile = observation

    override fun resolve(identifier: RestrictionProfileIdentifier): RestrictionProfile? =
        profiles.firstOrNull { profile -> profile.identifier == identifier }
}
