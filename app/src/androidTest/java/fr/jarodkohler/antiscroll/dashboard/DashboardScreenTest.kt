package fr.jarodkohler.antiscroll.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.jarodkohler.antiscroll.MonitoredApplicationUiModel
import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName
import fr.jarodkohler.antiscroll.domain.observation.AccessibilityMonitoringStatus
import fr.jarodkohler.antiscroll.domain.observation.CollectionStatus
import fr.jarodkohler.antiscroll.domain.observation.DailyApplicationUsage
import fr.jarodkohler.antiscroll.domain.observation.DataCompleteness
import fr.jarodkohler.antiscroll.domain.observation.MonitoringHealth
import fr.jarodkohler.antiscroll.domain.observation.UsageAccessStatus
import fr.jarodkohler.antiscroll.ui.theme.AntiScrollTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dailyUsageAndHealthAreDisplayedAndRefreshInvokesCallback() {
        val tiktokPackage = ApplicationPackageName("com.zhiliaoapp.musically")
        var refreshRequested = false

        composeRule.setContent {
            AntiScrollTheme {
                DashboardScreen(
                    uiState = DashboardUiState(
                        date = LocalDate.of(2026, 7, 28),
                        dailyUsage = listOf(
                            DailyApplicationUsage(
                                date = LocalDate.of(2026, 7, 28),
                                packageName = tiktokPackage,
                                foregroundDuration = Duration.ofSeconds(66),
                                estimatedOpeningCount = 1,
                                completeness = DataCompleteness.PARTIAL
                            )
                        ),
                        monitoringHealth = MonitoringHealth(
                            usageAccessStatus = UsageAccessStatus.GRANTED,
                            accessibilityStatus = AccessibilityMonitoringStatus.UNSUPPORTED,
                            collectionStatus = CollectionStatus.DEGRADED,
                            lastSuccessfulReconciliationAt = Instant.parse("2026-07-28T18:00:00Z")
                        )
                    ),
                    applications = listOf(
                        MonitoredApplicationUiModel(
                            packageName = tiktokPackage,
                            label = "TikTok",
                            icon = null,
                            isEnabled = true
                        )
                    ),
                    onRefresh = { refreshRequested = true }
                )
            }
        }

        composeRule.onNodeWithTag(DashboardTestTags.SUMMARY).assertIsDisplayed()
        composeRule.onNodeWithTag(DashboardTestTags.HEALTH).assertIsDisplayed()
        composeRule.onNodeWithText("1m 6s").assertIsDisplayed()
        composeRule.onNodeWithText("1m 6s • 1 opening").assertIsDisplayed()
        composeRule.onNodeWithText("TikTok").assertIsDisplayed()
        composeRule.onNodeWithText("Partial").assertIsDisplayed()
        composeRule.onNodeWithText("Some usage may be missing").assertIsDisplayed()
        composeRule.onNodeWithTag(DashboardTestTags.application(tiktokPackage)).assertIsDisplayed()
        composeRule.onNodeWithTag(DashboardTestTags.REFRESH).performClick()

        composeRule.runOnIdle {
            assertTrue(refreshRequested)
        }
    }

    @Test
    fun noConfiguredApplicationsShowsExplicitEmptyState() {
        composeRule.setContent {
            AntiScrollTheme {
                DashboardScreen(
                    uiState = DashboardUiState(date = LocalDate.of(2026, 7, 28)),
                    applications = emptyList(),
                    onRefresh = {}
                )
            }
        }

        composeRule.onNodeWithTag(DashboardTestTags.EMPTY).assertIsDisplayed()
    }
}
