package fr.jarodkohler.antiscroll.domain.observation

import java.time.Instant

enum class UsageAccessStatus {
    GRANTED,
    MISSING,
    UNAVAILABLE,
    ERROR
}

enum class AccessibilityMonitoringStatus {
    DISABLED,
    ENABLED,
    DISCONNECTED,
    UNSUPPORTED,
    ERROR
}

enum class CollectionStatus {
    NOT_STARTED,
    HEALTHY,
    DEGRADED,
    UNAVAILABLE
}

data class MonitoringHealth(
    val usageAccessStatus: UsageAccessStatus,
    val accessibilityStatus: AccessibilityMonitoringStatus,
    val collectionStatus: CollectionStatus,
    val lastSuccessfulReconciliationAt: Instant?
) {
    init {
        if (collectionStatus == CollectionStatus.HEALTHY) {
            require(usageAccessStatus == UsageAccessStatus.GRANTED) {
                "Healthy collection requires usage access"
            }
            require(lastSuccessfulReconciliationAt != null) {
                "Healthy collection requires a successful reconciliation"
            }
        }
    }
}
