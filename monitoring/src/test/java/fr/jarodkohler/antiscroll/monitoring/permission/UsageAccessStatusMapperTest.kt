package fr.jarodkohler.antiscroll.monitoring.permission

import android.app.AppOpsManager
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageAccessStatusMapperTest {
    @Test
    fun `allowed app operation grants usage access`() {
        assertEquals(
            UsageAccessStatus.GRANTED,
            usageAccessStatusForMode(AppOpsManager.MODE_ALLOWED)
        )
    }

    @Test
    fun `all non allowed modes report missing usage access`() {
        listOf(
            AppOpsManager.MODE_IGNORED,
            AppOpsManager.MODE_ERRORED,
            AppOpsManager.MODE_DEFAULT
        ).forEach { mode ->
            assertEquals(
                UsageAccessStatus.MISSING,
                usageAccessStatusForMode(mode)
            )
        }
    }
}
