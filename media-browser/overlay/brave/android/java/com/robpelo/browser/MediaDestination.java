/* Copyright (c) 2026 RobPelo contributors.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package com.robpelo.browser;

import java.net.URI;
import java.util.Locale;

enum MediaDestination {
    NETFLIX(
            "netflix",
            "https://www.netflix.com/browse",
            "netflix.com",
            "netflix.com",
            false),
    YOUTUBE(
            "youtube",
            "https://m.youtube.com/",
            "youtube.com,youtu.be,google.com",
            "youtube.com,youtu.be",
            false),
    HBO_MAX(
            "hbo_max",
            "https://play.hbomax.com/",
            "hbomax.com,max.com",
            "hbomax.com,max.com",
            true),
    PRIME_VIDEO(
            "prime_video",
            "https://www.primevideo.com/region/na/",
            "primevideo.com,amazon.com",
            "primevideo.com",
            true),
    APPLE_TV(
            "apple_tv",
            "https://tv.apple.com/",
            "apple.com,icloud.com",
            "tv.apple.com",
            false);

    enum Presentation {
        FRAMELESS,
        SHOW_ORIGIN,
        REJECT
    }

    private static final String[] SENSITIVE_PATH_PARTS = {
        "account", "billing", "login", "payment", "signin", "signup"
    };

    private final String mServiceId;
    private final String mStartUrl;
    private final String mAllowedDomains;
    private final String mMediaDomains;
    private final boolean mDesktopUserAgent;

    MediaDestination(
            String serviceId,
            String startUrl,
            String allowedDomains,
            String mediaDomains,
            boolean desktopUserAgent) {
        mServiceId = serviceId;
        mStartUrl = startUrl;
        mAllowedDomains = allowedDomains;
        mMediaDomains = mediaDomains;
        mDesktopUserAgent = desktopUserAgent;
    }

    static MediaDestination fromServiceId(String serviceId) {
        if (serviceId == null) {
            return null;
        }
        for (MediaDestination destination : values()) {
            if (destination.mServiceId.equals(serviceId)) {
                return destination;
            }
        }
        return null;
    }

    String getServiceId() {
        return mServiceId;
    }

    String getStartUrl() {
        return mStartUrl;
    }

    Presentation presentationForUrl(String url) {
        URI uri = parse(url);
        if (uri == null) {
            return Presentation.REJECT;
        }
        if ("about".equals(uri.getScheme()) && "blank".equals(uri.getSchemeSpecificPart())) {
            return Presentation.FRAMELESS;
        }
        if (!"https".equals(uri.getScheme()) || !matchesAny(uri.getHost(), mAllowedDomains)) {
            return Presentation.REJECT;
        }
        String pathAndQuery =
                (uri.getRawPath() == null ? "" : uri.getRawPath())
                        + "?"
                        + (uri.getRawQuery() == null ? "" : uri.getRawQuery());
        if (!matchesAny(uri.getHost(), mMediaDomains) || isSensitivePath(pathAndQuery)) {
            return Presentation.SHOW_ORIGIN;
        }
        return Presentation.FRAMELESS;
    }

    boolean usesDesktopUserAgent(String url) {
        URI uri = parse(url);
        return mDesktopUserAgent
                && uri != null
                && "https".equals(uri.getScheme())
                && matchesAny(uri.getHost(), mMediaDomains);
    }

    private static URI parse(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        try {
            return URI.create(url);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean matchesAny(String host, String domains) {
        if (host == null) {
            return false;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        for (String domain : domains.split(",")) {
            if (normalizedHost.equals(domain) || normalizedHost.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSensitivePath(String path) {
        if (path == null) {
            return false;
        }
        String normalizedPath = path.toLowerCase(Locale.ROOT);
        for (String part : SENSITIVE_PATH_PARTS) {
            if (normalizedPath.contains(part)) {
                return true;
            }
        }
        return false;
    }
}
