package fr.jarodkohler.antiscroll

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApplicationBootstrapInstrumentedTest {
    @Test
    fun registeredApplicationUsesHiltBootstrap() {
        val application = InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .applicationContext

        assertTrue(application is AntiScrollApplication)
    }
}
