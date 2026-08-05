package fr.jarodkohler.antiscroll.restriction

import java.time.Duration

data class UsageStatsSessionReconciliationPolicy(
    val pollingInterval: Duration,
    val overlap: Duration
) {
    init {
        require(!pollingInterval.isZero && !pollingInterval.isNegative) {
            "UsageStats reconciliation polling interval must be positive"
        }
        require(!overlap.isZero && !overlap.isNegative) {
            "UsageStats reconciliation overlap must be positive"
        }
    }
}
