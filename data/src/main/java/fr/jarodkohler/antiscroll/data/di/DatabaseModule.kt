package fr.jarodkohler.antiscroll.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.jarodkohler.antiscroll.data.local.AntiScrollDatabase
import fr.jarodkohler.antiscroll.data.local.AntiScrollDatabaseSchema
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAntiScrollDatabase(
        @ApplicationContext context: Context,
    ): AntiScrollDatabase = Room.databaseBuilder(
        context,
        AntiScrollDatabase::class.java,
        AntiScrollDatabaseSchema.NAME,
    ).build()
}
