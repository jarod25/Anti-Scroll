package fr.jarodkohler.antiscroll.applicationconfig

import fr.jarodkohler.antiscroll.domain.application.ApplicationPackageName

data class SupportedApplication(
    val packageName: ApplicationPackageName,
    val fallbackLabel: String
)

object SupportedApplicationCatalog {
    const val VERSION = 1

    val applications: List<SupportedApplication> = listOf(
        SupportedApplication(
            packageName = ApplicationPackageName("com.zhiliaoapp.musically"),
            fallbackLabel = "TikTok"
        ),
        SupportedApplication(
            packageName = ApplicationPackageName("com.instagram.android"),
            fallbackLabel = "Instagram"
        ),
        SupportedApplication(
            packageName = ApplicationPackageName("com.instagram.barcelona"),
            fallbackLabel = "Threads"
        ),
        SupportedApplication(
            packageName = ApplicationPackageName("com.reddit.frontpage"),
            fallbackLabel = "Reddit"
        ),
        SupportedApplication(
            packageName = ApplicationPackageName("com.twitter.android"),
            fallbackLabel = "X"
        ),
        SupportedApplication(
            packageName = ApplicationPackageName("com.facebook.katana"),
            fallbackLabel = "Facebook"
        ),
        SupportedApplication(
            packageName = ApplicationPackageName("com.snapchat.android"),
            fallbackLabel = "Snapchat"
        )
    )
}
