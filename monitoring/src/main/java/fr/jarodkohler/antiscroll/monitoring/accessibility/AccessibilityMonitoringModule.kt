package fr.jarodkohler.antiscroll.monitoring.accessibility

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.jarodkohler.antiscroll.domain.monitoring.ForegroundApplicationSignalSource

@Module
@InstallIn(SingletonComponent::class)
abstract class AccessibilityMonitoringModule {
    @Binds
    abstract fun bindForegroundApplicationSignalSource(
        source: AccessibilityForegroundApplicationSignalSource
    ): ForegroundApplicationSignalSource
}
