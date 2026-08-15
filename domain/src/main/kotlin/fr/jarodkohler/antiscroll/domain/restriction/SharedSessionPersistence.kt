package fr.jarodkohler.antiscroll.domain.restriction

@JvmInline
value class DeviceBootIdentifier(val value: Int) {
    init {
        require(value >= 0) { "Device boot identifier must not be negative" }
    }
}

data class SharedSessionCheckpoint(
    val profileIdentifier: RestrictionProfileIdentifier,
    val profileVersion: Int,
    val bootIdentifier: DeviceBootIdentifier,
    val state: SharedSessionState.Active
) {
    init {
        require(profileVersion > 0) { "Checkpoint profile version must be positive" }
    }

    companion object {
        fun from(
            profile: RestrictionProfile,
            state: SharedSessionState.Active,
            bootIdentifier: DeviceBootIdentifier
        ): SharedSessionCheckpoint = SharedSessionCheckpoint(
            profileIdentifier = profile.identifier,
            profileVersion = profile.version,
            bootIdentifier = bootIdentifier,
            state = state
        )
    }
}

interface SharedSessionStateRepository {
    suspend fun load(): SharedSessionCheckpoint?

    suspend fun save(checkpoint: SharedSessionCheckpoint)

    suspend fun clear()
}
