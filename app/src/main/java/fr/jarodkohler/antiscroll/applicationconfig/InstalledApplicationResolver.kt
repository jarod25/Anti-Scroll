package fr.jarodkohler.antiscroll.applicationconfig

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApplicationPresentation(
    val packageName: ApplicationPackageName,
    val label: String,
    val icon: ImageBitmap?
)

interface InstalledApplicationResolver {
    suspend fun resolveInstalled(catalog: List<SupportedApplication>): List<InstalledApplicationPresentation>
}

@Singleton
class AndroidInstalledApplicationResolver @Inject constructor(@param:ApplicationContext private val context: Context) :
    InstalledApplicationResolver {
    private val packageManager = context.packageManager

    override suspend fun resolveInstalled(catalog: List<SupportedApplication>): List<InstalledApplicationPresentation> =
        withContext(Dispatchers.IO) {
            catalog.mapNotNull(::resolveInstalledApplication)
                .sortedWith(
                    compareBy(String.CASE_INSENSITIVE_ORDER) { application -> application.label }
                )
        }

    private fun resolveInstalledApplication(
        supportedApplication: SupportedApplication
    ): InstalledApplicationPresentation? {
        val applicationInfo = runCatching {
            applicationInfo(supportedApplication.packageName.value)
        }.getOrNull() ?: return null

        val label = runCatching {
            packageManager.getApplicationLabel(applicationInfo).toString().trim()
        }.getOrNull().takeUnless { value -> value.isNullOrBlank() }
            ?: supportedApplication.fallbackLabel

        val icon = runCatching {
            packageManager.getApplicationIcon(applicationInfo)
                .toBitmap(width = ICON_SIZE_PX, height = ICON_SIZE_PX)
                .asImageBitmap()
        }.getOrNull()

        return InstalledApplicationPresentation(
            packageName = supportedApplication.packageName,
            label = label,
            icon = icon
        )
    }

    @Suppress("DEPRECATION")
    private fun applicationInfo(packageName: String): ApplicationInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0)
            )
        } else {
            packageManager.getApplicationInfo(packageName, 0)
        }

    private companion object {
        const val ICON_SIZE_PX = 96
    }
}
