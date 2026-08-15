package fr.jarodkohler.antiscroll.applicationconfig

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.jarodkohler.antiscroll.MainUiState
import fr.jarodkohler.antiscroll.MonitoredApplicationUiModel
import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.ui.theme.AntiScrollTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonitoredApplicationsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun installedApplicationsAreShownAndSwitchInvokesCallback() {
        val instagramPackage = ApplicationPackageName("com.instagram.android")
        var changedPackage: ApplicationPackageName? = null
        var changedEnabled = false

        composeRule.setContent {
            AntiScrollTheme {
                MonitoredApplicationsScreen(
                    uiState = MainUiState(
                        applications = listOf(
                            MonitoredApplicationUiModel(
                                packageName = instagramPackage,
                                label = "Instagram",
                                icon = null,
                                isEnabled = false
                            )
                        )
                    ),
                    settingsLaunchFailed = false,
                    onSetApplicationEnabled = { packageName, isEnabled ->
                        changedPackage = packageName
                        changedEnabled = isEnabled
                    },
                    onRetry = {},
                    onReviewUsageAccessSettings = {}
                )
            }
        }

        composeRule.onNodeWithText("Instagram").assertIsDisplayed()
        composeRule.onNodeWithText("com.instagram.android").assertIsDisplayed()
        composeRule.onNodeWithTag(MonitoredApplicationsTestTags.switch(instagramPackage))
            .assertIsOff()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(instagramPackage, changedPackage)
            assertTrue(changedEnabled)
        }
    }

    @Test
    fun emptyInstalledCatalogShowsExplicitState() {
        composeRule.setContent {
            AntiScrollTheme {
                MonitoredApplicationsScreen(
                    uiState = MainUiState(),
                    settingsLaunchFailed = false,
                    onSetApplicationEnabled = { _, _ -> },
                    onRetry = {},
                    onReviewUsageAccessSettings = {}
                )
            }
        }

        composeRule.onNodeWithTag(MonitoredApplicationsTestTags.EMPTY)
            .assertIsDisplayed()
    }
}
