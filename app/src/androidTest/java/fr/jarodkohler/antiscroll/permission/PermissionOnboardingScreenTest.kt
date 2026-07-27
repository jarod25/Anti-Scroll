package fr.jarodkohler.antiscroll.permission

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.jarodkohler.antiscroll.domain.observation.AccessibilityMonitoringStatus
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus
import fr.jarodkohler.antiscroll.monitoring.permission.MonitoringPermissionSnapshot
import fr.jarodkohler.antiscroll.ui.theme.AntiScrollTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionOnboardingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun missingUsageAccessShowsActionAndInvokesCallback() {
        var clicked = false

        composeRule.setContent {
            AntiScrollTheme {
                PermissionOnboardingScreen(
                    permissionSnapshot = MonitoringPermissionSnapshot(
                        usageAccessStatus = UsageAccessStatus.MISSING,
                        accessibilityStatus = AccessibilityMonitoringStatus.UNSUPPORTED
                    ),
                    settingsLaunchFailed = false,
                    onOpenUsageAccessSettings = { clicked = true }
                )
            }
        }

        composeRule.onNodeWithText("Required").assertIsDisplayed()
        composeRule.onNodeWithTag(PermissionOnboardingTestTags.USAGE_ACCESS_BUTTON)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(clicked)
        }
    }

    @Test
    fun grantedUsageAccessAndOptionalAccessibilityAreShownSeparately() {
        composeRule.setContent {
            AntiScrollTheme {
                PermissionOnboardingScreen(
                    permissionSnapshot = MonitoringPermissionSnapshot(
                        usageAccessStatus = UsageAccessStatus.GRANTED,
                        accessibilityStatus = AccessibilityMonitoringStatus.UNSUPPORTED
                    ),
                    settingsLaunchFailed = true,
                    onOpenUsageAccessSettings = {}
                )
            }
        }

        composeRule.onNodeWithText("Granted").assertIsDisplayed()
        composeRule.onNodeWithText("Not available yet").assertIsDisplayed()
        composeRule.onNodeWithTag(PermissionOnboardingTestTags.SETTINGS_ERROR)
            .assertIsDisplayed()
    }
}
