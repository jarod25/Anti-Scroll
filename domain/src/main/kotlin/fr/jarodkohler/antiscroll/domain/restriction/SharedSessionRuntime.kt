package fr.jarodkohler.antiscroll.domain.restriction

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface RestrictionProfileSource {
    val activeProfile: StateFlow<RestrictionProfile>
}

interface SharedSessionEventSource {
    val events: Flow<SharedSessionEvent>
}

data class SharedSessionRuntimeSnapshot(
    val profile: RestrictionProfile,
    val state: SharedSessionState,
    val lastTransition: SharedSessionTransition?,
    val lastDecision: RestrictionDecision?
)

interface SharedSessionRuntimeStateSource {
    val snapshots: StateFlow<SharedSessionRuntimeSnapshot>
}
