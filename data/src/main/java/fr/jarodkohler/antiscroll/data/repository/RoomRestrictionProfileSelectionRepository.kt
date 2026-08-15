package fr.jarodkohler.antiscroll.data.repository

import fr.jarodkohler.antiscroll.data.local.AntiScrollDatabase
import fr.jarodkohler.antiscroll.data.local.RestrictionProfileSelectionEntity
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfileIdentifier
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfileSelectionRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomRestrictionProfileSelectionRepository
@Inject
constructor(database: AntiScrollDatabase) : RestrictionProfileSelectionRepository {
    private val dao = database.restrictionProfileSelectionDao()

    override suspend fun load(): RestrictionProfileIdentifier? =
        dao.get()?.profileIdentifier?.let(::RestrictionProfileIdentifier)

    override suspend fun save(identifier: RestrictionProfileIdentifier) {
        dao.save(
            RestrictionProfileSelectionEntity(
                singletonId = SINGLETON_ID,
                profileIdentifier = identifier.value
            )
        )
    }

    private companion object {
        const val SINGLETON_ID = 1
    }
}
