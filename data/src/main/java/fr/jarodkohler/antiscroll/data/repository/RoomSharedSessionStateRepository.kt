package fr.jarodkohler.antiscroll.data.repository

import fr.jarodkohler.antiscroll.data.local.AntiScrollDatabase
import fr.jarodkohler.antiscroll.data.local.SharedSessionStateEntity
import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.restriction.DeviceBootIdentifier
import fr.jarodkohler.antiscroll.domain.restriction.RestrictionProfileIdentifier
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionCheckpoint
import fr.jarodkohler.antiscroll.domain.restriction.SharedSessionStateRepository
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomSharedSessionStateRepository
@Inject
constructor(database: AntiScrollDatabase) : SharedSessionStateRepository {
    private val dao = database.sharedSessionStateDao()

    override suspend fun load(): SharedSessionCheckpoint? = dao.get()?.toDomain()

    override suspend fun save(checkpoint: SharedSessionCheckpoint) {
        dao.save(checkpoint.toEntity())
    }

    override suspend fun clear() {
        dao.clear()
    }

    private fun SharedSessionStateEntity.toDomain(): SharedSessionCheckpoint = SharedSessionCheckpoint(
        profileIdentifier = RestrictionProfileIdentifier(profileIdentifier),
        profileVersion = profileVersion,
        bootIdentifier = DeviceBootIdentifier(bootIdentifier),
        startedAt = Instant.ofEpochMilli(startedAtEpochMillis),
        startedAtElapsedRealtime = Duration.ofMillis(startedAtElapsedRealtimeMillis),
        accumulatedForegroundDuration = Duration.ofMillis(accumulatedForegroundDurationMillis),
        foregroundApplication = foregroundPackageName?.let(::ApplicationPackageName),
        foregroundSinceElapsedRealtime = foregroundSinceElapsedRealtimeMillis?.let(Duration::ofMillis),
        inactiveSinceElapsedRealtime = inactiveSinceElapsedRealtimeMillis?.let(Duration::ofMillis),
        lastObservedAt = Instant.ofEpochMilli(lastObservedAtEpochMillis),
        lastObservedElapsedRealtime = Duration.ofMillis(lastObservedElapsedRealtimeMillis)
    )

    private fun SharedSessionCheckpoint.toEntity(): SharedSessionStateEntity = SharedSessionStateEntity(
        singletonId = SINGLETON_ID,
        profileIdentifier = profileIdentifier.value,
        profileVersion = profileVersion,
        bootIdentifier = bootIdentifier.value,
        startedAtEpochMillis = startedAt.toEpochMilli(),
        startedAtElapsedRealtimeMillis = startedAtElapsedRealtime.toMillis(),
        accumulatedForegroundDurationMillis = accumulatedForegroundDuration.toMillis(),
        foregroundPackageName = foregroundApplication?.value,
        foregroundSinceElapsedRealtimeMillis = foregroundSinceElapsedRealtime?.toMillis(),
        inactiveSinceElapsedRealtimeMillis = inactiveSinceElapsedRealtime?.toMillis(),
        lastObservedAtEpochMillis = lastObservedAt.toEpochMilli(),
        lastObservedElapsedRealtimeMillis = lastObservedElapsedRealtime.toMillis()
    )

    private companion object {
        const val SINGLETON_ID = 1
    }
}
