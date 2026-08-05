package fr.jarodkohler.antiscroll

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import fr.jarodkohler.antiscroll.restriction.SharedSessionRuntime
import javax.inject.Inject

@HiltAndroidApp
class AntiScrollApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var sharedSessionRuntime: SharedSessionRuntime

    override fun onCreate() {
        super.onCreate()
        sharedSessionRuntime.start()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
