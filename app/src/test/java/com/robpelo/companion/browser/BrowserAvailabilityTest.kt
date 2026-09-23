package com.robpelo.companion.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserAvailabilityTest {
    @Test
    fun `missing package is reported`() {
        assertEquals(
            BrowserLaunchResult.MissingPackage(PACKAGE),
            BrowserAvailability.validate(null, PACKAGE, 2, true),
        )
    }

    @Test
    fun `disabled package is reported before other checks`() {
        assertEquals(
            BrowserLaunchResult.DisabledPackage(PACKAGE),
            BrowserAvailability.validate(snapshot(enabled = false), PACKAGE, 2, true),
        )
    }

    @Test
    fun `signature mismatch is reported`() {
        assertEquals(
            BrowserLaunchResult.SignatureMismatch(PACKAGE),
            BrowserAvailability.validate(
                snapshot(signatureMatches = false),
                PACKAGE,
                2,
                true,
            ),
        )
    }

    @Test
    fun `old version is reported`() {
        assertEquals(
            BrowserLaunchResult.IncompatibleVersion(PACKAGE, 1, 2),
            BrowserAvailability.validate(snapshot(versionCode = 1), PACKAGE, 2, true),
        )
    }

    @Test
    fun `missing activity is reported`() {
        assertEquals(
            BrowserLaunchResult.MissingActivity(PACKAGE),
            BrowserAvailability.validate(
                snapshot(activityPresent = false),
                PACKAGE,
                2,
                true,
            ),
        )
    }

    @Test
    fun `available package has no failure`() {
        assertNull(BrowserAvailability.validate(snapshot(), PACKAGE, 2, true))
    }

    @Test
    fun `rollback launcher may opt out of signature matching`() {
        assertNull(
            BrowserAvailability.validate(
                snapshot(signatureMatches = false),
                PACKAGE,
                2,
                false,
            ),
        )
    }

    private fun snapshot(
        enabled: Boolean = true,
        versionCode: Long = 2,
        signatureMatches: Boolean = true,
        activityPresent: Boolean = true,
        activityEnabled: Boolean = true,
    ) = BrowserPackageSnapshot(
        packageName = PACKAGE,
        enabled = enabled,
        versionCode = versionCode,
        signatureMatches = signatureMatches,
        activityPresent = activityPresent,
        activityEnabled = activityEnabled,
    )

    private companion object {
        const val PACKAGE = "com.robpelo.browser"
    }
}
