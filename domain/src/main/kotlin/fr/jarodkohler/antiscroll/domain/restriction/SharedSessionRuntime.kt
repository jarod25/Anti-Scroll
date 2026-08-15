package fr.jarodkohler.antiscroll.domain.restriction

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface RestrictionProfileSource {
    val activeProfile: StateFlow<RestrictionProfile>
}

interface SharedSessionEventSource {
    val events: Flow<SharedSessionEvent>
}

enum class SharedSessionRuntimeStatus {
    STARTING,
    READY
}

data class SharedSessionRuntimeSnapshot(
    val status: SharedSessionRuntimeStatus,
    val profile: RestrictionProfile,
    val state: SharedSessionState,
    val lastTransition: SharedSessionTransition?,
    val lastDecision: RestrictionDecision?
)

interface SharedSessionRuntimeStateSource {
    val snapshots: StateFlow<SharedSessionRuntimeSnapshot>
}
