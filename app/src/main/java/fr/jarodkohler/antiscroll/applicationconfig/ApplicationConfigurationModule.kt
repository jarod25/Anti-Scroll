package fr.jarodkohler.antiscroll.applicationconfig

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ApplicationConfigurationBindingModule {
    @Binds
    @Singleton
    abstract fun bindInstalledApplicationResolver(
        resolver: AndroidInstalledApplicationResolver
    ): InstalledApplicationResolver
}

@Module
@InstallIn(SingletonComponent::class)
object ApplicationConfigurationRuntimeModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()
}
