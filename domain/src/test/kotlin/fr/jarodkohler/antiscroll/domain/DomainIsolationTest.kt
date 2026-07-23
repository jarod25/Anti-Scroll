package fr.jarodkohler.antiscroll.domain

import org.junit.Assert.assertThrows
import org.junit.Test

class DomainIsolationTest {
    @Test
    fun `domain classpath does not expose Android framework`() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("android.content.Context")
        }
    }
}
