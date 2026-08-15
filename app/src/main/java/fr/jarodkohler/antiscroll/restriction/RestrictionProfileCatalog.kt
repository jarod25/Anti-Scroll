package fr.jarodkohler.antiscroll.restriction

import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfile
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfileIdentifier

interface RestrictionProfileCatalog {
    val defaultProfile: RestrictionProfile

    fun resolve(identifier: RestrictionProfileIdentifier): RestrictionProfile?
}
