package com.robpelo.companion.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirefoxVersionTest {
    @Test
    fun `update is available when Firefox is not installed`() {
        assertTrue(FirefoxVersion.updateAvailable("156.0", null))
    }

    @Test
    fun `new major version is newer`() {
        assertTrue(FirefoxVersion.isNewer("156.0", "134.0"))
    }

    @Test
    fun `new patch version is newer`() {
        assertTrue(FirefoxVersion.isNewer("156.0.1", "156.0"))
    }

    @Test
    fun `same version is not newer`() {
        assertFalse(FirefoxVersion.isNewer("156.0", "156.0"))
    }

    @Test
    fun `older version is not newer`() {
        assertFalse(FirefoxVersion.isNewer("155.9", "156.0"))
    }
}
