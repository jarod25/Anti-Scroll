package fr.jarodkohler.antiscroll.domain.application

import java.time.Instant

/** User-selected application configuration without Android presentation metadata. */
data class MonitoredApplication(val packageName: ApplicationPackageName, val isEnabled: Boolean, val addedAt: Instant)
