package fr.jarodkohler.antiscroll.permission

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
    fun missingUsageAccessShowsPrivacyDisclosureAndInvokesCallback() {
        var clicked = false

        composeRule.setContent {
            AntiScrollTheme {
                PermissionOnboardingScreen(
                    permissionSnapshot = MonitoringPermissionSnapshot(
                        usageAccessStatus = UsageAccessStatus.MISSING,
                        accessibilityStatus = AccessibilityMonitoringStatus.DISABLED
                    ),
                    settingsLaunchFailed = false,
                    onOpenUsageAccessSettings = { clicked = true },
                    onOpenAccessibilitySettings = {}
                )
            }
        }

        composeRule.onNodeWithText("Required").assertIsDisplayed()
        composeRule.onNodeWithTag(PermissionOnboardingTestTags.PRIVACY_SUMMARY_CARD)
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            "The accessibility service does not retrieve screen contents, visible text, messages, passwords, typed text, photos or accessibility nodes."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Continue to Android settings").assertIsDisplayed()
        composeRule.onNodeWithTag(PermissionOnboardingTestTags.USAGE_ACCESS_BUTTON)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(clicked)
        }
    }

    @Test
    fun accessibilityStatusAndSettingsActionAreShownSeparately() {
        var clicked = false

        composeRule.setContent {
            AntiScrollTheme {
                PermissionOnboardingScreen(
                    permissionSnapshot = MonitoringPermissionSnapshot(
                        usageAccessStatus = UsageAccessStatus.GRANTED,
                        accessibilityStatus = AccessibilityMonitoringStatus.DISCONNECTED
                    ),
                    settingsLaunchFailed = true,
                    onOpenUsageAccessSettings = {},
                    onOpenAccessibilitySettings = { clicked = true }
                )
            }
        }

        composeRule.onNodeWithText("Granted").assertIsDisplayed()
        composeRule.onNodeWithText("Disconnected").assertIsDisplayed()
        composeRule.onNodeWithTag(PermissionOnboardingTestTags.ACCESSIBILITY_BUTTON)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(PermissionOnboardingTestTags.SETTINGS_ERROR)
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.runOnIdle {
            assertTrue(clicked)
        }
    }
}
