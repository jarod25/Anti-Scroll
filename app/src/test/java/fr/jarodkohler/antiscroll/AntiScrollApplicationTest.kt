package fr.jarodkohler.antiscroll

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AntiScrollApplicationTest {
    @Test
    fun accessibilityProcessStartsSharedSessionRuntime() {
        assertTrue(
            shouldStartSharedSessionRuntime(
                applicationPackageName = "fr.jarodkohler.antiscroll",
                processName = "fr.jarodkohler.antiscroll:accessibility"
            )
        )
    }

    @Test
    fun defaultProcessDoesNotStartSharedSessionRuntime() {
        assertFalse(
            shouldStartSharedSessionRuntime(
                applicationPackageName = "fr.jarodkohler.antiscroll",
                processName = "fr.jarodkohler.antiscroll"
            )
        )
    }

    @Test
    fun deviceTestAccessibilityProcessUsesVariantPackageName() {
        assertTrue(
            shouldStartSharedSessionRuntime(
                applicationPackageName = "fr.jarodkohler.antiscroll.instrumented",
                processName = "fr.jarodkohler.antiscroll.instrumented:accessibility"
            )
        )
    }
}
