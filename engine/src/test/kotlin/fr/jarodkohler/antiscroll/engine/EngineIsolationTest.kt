package fr.jarodkohler.antiscroll.engine

import org.junit.Assert.assertThrows
import org.junit.Test

class EngineIsolationTest {
    @Test
    fun `engine classpath does not expose Android framework`() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("android.content.Context")
        }
    }
}
