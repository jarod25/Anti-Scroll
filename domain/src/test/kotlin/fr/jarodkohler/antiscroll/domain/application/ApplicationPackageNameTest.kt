package fr.jarodkohler.antiscroll.domain.application

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApplicationPackageNameTest {
    @Test
    fun `package name preserves the Android application identity`() {
        val packageName = ApplicationPackageName("com.zhiliaoapp.musically")

        assertEquals("com.zhiliaoapp.musically", packageName.value)
        assertEquals("com.zhiliaoapp.musically", packageName.toString())
    }

    @Test
    fun `package name rejects blank values`() {
        assertThrows(IllegalArgumentException::class.java) {
            ApplicationPackageName(" ")
        }
    }

    @Test
    fun `package name rejects whitespace`() {
        assertThrows(IllegalArgumentException::class.java) {
            ApplicationPackageName("com.example application")
        }
    }
}
