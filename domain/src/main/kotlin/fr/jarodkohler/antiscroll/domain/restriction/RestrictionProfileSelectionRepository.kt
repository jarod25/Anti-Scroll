package fr.jarodkohler.antiscroll.domain.restriction

interface RestrictionProfileSelectionRepository {
    suspend fun load(): RestrictionProfileIdentifier?

    suspend fun save(identifier: RestrictionProfileIdentifier)
}
