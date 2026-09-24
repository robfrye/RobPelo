/* Copyright (c) 2026 RobPelo contributors.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package com.robpelo.browser;

import static androidx.browser.customtabs.CustomTabsIntent.COLOR_SCHEME_DARK;
import static androidx.browser.customtabs.CustomTabsIntent.COLOR_SCHEME_LIGHT;
import static org.chromium.chrome.browser.customtabs.CustomTabIntentDataProvider.EXTRA_UI_TYPE;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Browser;
import android.widget.Toast;

import androidx.browser.customtabs.CustomTabsIntent;

import org.chromium.base.IntentUtils;
import org.chromium.base.Log;
import org.chromium.chrome.browser.browserservices.intents.BrowserServicesIntentDataProvider;
import org.chromium.ui.util.ColorUtils;

/** Validates RobPelo's public media contract before entering the browser's private activity. */
public final class MediaViewerActivity extends Activity {
    public static final String ACTION_OPEN_MEDIA = "com.robpelo.browser.action.OPEN_MEDIA";
    public static final String EXTRA_SERVICE = "service";

    private static final String COMPANION_PACKAGE = "com.robpelo.companion";
    private static final String TAG = "RobPeloMedia";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent request = getIntent();
        MediaDestination destination =
                ACTION_OPEN_MEDIA.equals(request.getAction())
                                && COMPANION_PACKAGE.equals(
                                        request.getStringExtra(Browser.EXTRA_APPLICATION_ID))
                        ? MediaDestination.fromServiceId(request.getStringExtra(EXTRA_SERVICE))
                        : null;
        if (destination == null) {
            Log.e(TAG, "Rejected an invalid media launch request");
            Toast.makeText(this, "Unsupported media destination.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Intent viewer =
                new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(destination.getStartUrl()),
                        this,
                        MediaViewerCustomTabActivity.class);
        viewer.putExtra(EXTRA_SERVICE, destination.getServiceId());
        viewer.putExtra(
                CustomTabsIntent.EXTRA_TITLE_VISIBILITY_STATE, CustomTabsIntent.NO_TITLE);
        viewer.putExtra(CustomTabsIntent.EXTRA_ENABLE_URLBAR_HIDING, false);
        viewer.putExtra(
                CustomTabsIntent.EXTRA_COLOR_SCHEME,
                ColorUtils.inNightMode(this) ? COLOR_SCHEME_DARK : COLOR_SCHEME_LIGHT);
        viewer.putExtra(
                EXTRA_UI_TYPE, BrowserServicesIntentDataProvider.CustomTabsUiType.INFO_PAGE);
        viewer.putExtra(Browser.EXTRA_APPLICATION_ID, getPackageName());
        IntentUtils.addTrustedIntentExtras(viewer);

        try {
            startActivity(viewer);
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to open the internal media viewer", exception);
            Toast.makeText(this, "The media viewer could not be opened.", Toast.LENGTH_LONG).show();
        } finally {
            finish();
        }
    }
}
