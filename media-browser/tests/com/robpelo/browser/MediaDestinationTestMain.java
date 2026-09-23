package com.robpelo.browser;

public final class MediaDestinationTestMain {
    public static void main(String[] args) {
        assertDestinationMapping();
        assertNavigationPolicies();
        assertUserAgentPolicies();
    }

    private static void assertDestinationMapping() {
        assertSame(MediaDestination.NETFLIX, MediaDestination.fromServiceId("netflix"));
        assertSame(MediaDestination.YOUTUBE, MediaDestination.fromServiceId("youtube"));
        assertSame(MediaDestination.HBO_MAX, MediaDestination.fromServiceId("hbo_max"));
        assertSame(MediaDestination.PRIME_VIDEO, MediaDestination.fromServiceId("prime_video"));
        assertSame(MediaDestination.APPLE_TV, MediaDestination.fromServiceId("apple_tv"));
        assertSame(null, MediaDestination.fromServiceId("https://example.com"));
        assertSame(null, MediaDestination.fromServiceId(null));
    }

    private static void assertNavigationPolicies() {
        assertSame(
                MediaDestination.Presentation.FRAMELESS,
                MediaDestination.NETFLIX.presentationForUrl("https://www.netflix.com/browse"));
        assertSame(
                MediaDestination.Presentation.SHOW_ORIGIN,
                MediaDestination.NETFLIX.presentationForUrl("https://www.netflix.com/login"));
        assertSame(
                MediaDestination.Presentation.SHOW_ORIGIN,
                MediaDestination.NETFLIX.presentationForUrl(
                        "https://www.netflix.com/browse?action=login"));
        assertSame(
                MediaDestination.Presentation.SHOW_ORIGIN,
                MediaDestination.YOUTUBE.presentationForUrl(
                        "https://accounts.google.com/signin"));
        assertSame(
                MediaDestination.Presentation.REJECT,
                MediaDestination.YOUTUBE.presentationForUrl("https://example.com/"));
        assertSame(
                MediaDestination.Presentation.REJECT,
                MediaDestination.APPLE_TV.presentationForUrl("http://tv.apple.com/"));
        assertSame(
                MediaDestination.Presentation.REJECT,
                MediaDestination.PRIME_VIDEO.presentationForUrl(
                        "https://notamazon.com/signin"));
        assertSame(
                MediaDestination.Presentation.REJECT,
                MediaDestination.NETFLIX.presentationForUrl(
                        "https://www.netflix.com@evil.example/"));
        assertSame(
                MediaDestination.Presentation.FRAMELESS,
                MediaDestination.NETFLIX.presentationForUrl("about:blank"));
        assertSame(
                MediaDestination.Presentation.REJECT,
                MediaDestination.NETFLIX.presentationForUrl(""));
        assertSame(
                MediaDestination.Presentation.REJECT,
                MediaDestination.NETFLIX.presentationForUrl(null));
    }

    private static void assertUserAgentPolicies() {
        assertTrue(
                MediaDestination.HBO_MAX.usesDesktopUserAgent(
                        "https://play.hbomax.com/movie"));
        assertFalse(
                MediaDestination.HBO_MAX.usesDesktopUserAgent(
                        "https://example.com/play.hbomax.com"));
        assertFalse(
                MediaDestination.YOUTUBE.usesDesktopUserAgent(
                        "https://m.youtube.com/"));
    }

    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new AssertionError("Expected true");
        }
    }

    private static void assertFalse(boolean value) {
        if (value) {
            throw new AssertionError("Expected false");
        }
    }
}
