package fr.jarodkohler.antiscroll.data.repository

import fr.jarodkohler.antiscroll.data.local.AntiScrollDatabase
import fr.jarodkohler.antiscroll.data.local.MonitoredApplicationEntity
import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.application.MonitoredApplication
import fr.jarodkohler.antiscroll.domain.observation.MonitoredApplicationRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomMonitoredApplicationRepository @Inject constructor(database: AntiScrollDatabase) :
    MonitoredApplicationRepository {
    private val dao = database.monitoredApplicationDao()

    override fun observeAll(): Flow<List<MonitoredApplication>> = dao.observeAll().map { entities ->
        entities.map(MonitoredApplicationEntity::toDomain)
    }

    override suspend fun enabledApplications(): List<MonitoredApplication> =
        dao.findEnabled().map(MonitoredApplicationEntity::toDomain)

    override suspend fun save(application: MonitoredApplication) {
        dao.upsert(application.toEntity())
    }
}

private fun MonitoredApplicationEntity.toDomain(): MonitoredApplication = MonitoredApplication(
    packageName = ApplicationPackageName(packageName),
    isEnabled = isEnabled,
    addedAt = Instant.ofEpochMilli(addedAtEpochMillis)
)

private fun MonitoredApplication.toEntity(): MonitoredApplicationEntity = MonitoredApplicationEntity(
    packageName = packageName.value,
    isEnabled = isEnabled,
    addedAtEpochMillis = addedAt.toEpochMilli()
)
