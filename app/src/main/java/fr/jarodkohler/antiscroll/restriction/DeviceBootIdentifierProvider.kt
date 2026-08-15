package fr.jarodkohler.antiscroll.restriction

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.jarodkohler.antiscroll.domain.restriction.DeviceBootIdentifier
import javax.inject.Inject
import javax.inject.Singleton

interface DeviceBootIdentifierProvider {
    fun current(): DeviceBootIdentifier
}

@Singleton
class AndroidDeviceBootIdentifierProvider
@Inject
constructor(@ApplicationContext private val context: Context) :
    DeviceBootIdentifierProvider {
    override fun current(): DeviceBootIdentifier = DeviceBootIdentifier(
        Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.BOOT_COUNT,
            0
        )
    )
}
