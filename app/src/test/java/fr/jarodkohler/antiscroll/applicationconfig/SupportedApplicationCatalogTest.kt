package fr.jarodkohler.antiscroll.applicationconfig

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedApplicationCatalogTest {
    @Test
    fun catalogPackageNamesAreUnique() {
        val packageNames = SupportedApplicationCatalog.applications.map { application ->
            application.packageName.value
        }

        assertEquals(packageNames.size, packageNames.distinct().size)
        assertTrue(SupportedApplicationCatalog.VERSION > 0)
    }

    @Test
    fun essentialCommunicationApplicationsAreExcluded() {
        val packageNames = SupportedApplicationCatalog.applications.mapTo(mutableSetOf()) { application ->
            application.packageName.value
        }
        val excludedCommunicationPackages = setOf(
            "com.whatsapp",
            "com.discord",
            "jp.naver.line.android",
            "com.google.android.apps.messaging"
        )

        excludedCommunicationPackages.forEach { packageName ->
            assertFalse(packageName in packageNames)
        }
    }
}
