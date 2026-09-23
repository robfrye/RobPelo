/* Copyright (c) 2026 RobPelo contributors.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package com.robpelo.browser;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.chromium.base.TerminationStatus;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.customtabs.FullScreenCustomTabActivity;
import org.chromium.chrome.browser.customtabs.content.CustomTabActivityTabProvider;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabUtils;
import org.chromium.content_public.browser.NavigationHandle;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.browser.WebContentsObserver;

/** Chrome-free media surface backed by Brave's normal persistent profile. */
public final class MediaViewerCustomTabActivity extends FullScreenCustomTabActivity {
    private static final String TAG = "RobPeloMedia";
    private static final int MAX_WEB_CONTENTS_RETRIES = 20;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private MediaDestination mDestination;
    private CustomTabActivityTabProvider.Observer mTabProviderObserver;
    private WebContentsObserver mWebContentsObserver;
    private boolean mBlockingNavigation;
    private int mWebContentsRetries;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mDestination =
                MediaDestination.fromServiceId(
                        getIntent().getStringExtra(MediaViewerActivity.EXTRA_SERVICE));
        super.onCreate(savedInstanceState);
        if (mDestination == null) {
            Log.e(TAG, "Internal media viewer started without a valid destination");
            finish();
        }
    }

    @Override
    public void performPostInflationStartup() {
        super.performPostInflationStartup();
        if (mDestination == null) {
            return;
        }

        CustomTabActivityTabProvider tabProvider = getCustomTabActivityTabProvider();
        mTabProviderObserver =
                new CustomTabActivityTabProvider.Observer() {
                    @Override
                    public void onInitialTabCreated(Tab tab, int mode) {
                        observeTab(tab);
                    }

                    @Override
                    public void onTabSwapped(Tab tab) {
                        observeTab(tab);
                    }
                };
        tabProvider.addObserver(mTabProviderObserver);
        Tab existingTab = tabProvider.getTab();
        if (existingTab != null) {
            observeTab(existingTab);
        }
    }

    @Override
    protected void onDestroy() {
        if (mTabProviderObserver != null) {
            getCustomTabActivityTabProvider().removeObserver(mTabProviderObserver);
            mTabProviderObserver = null;
        }
        if (mWebContentsObserver != null) {
            mWebContentsObserver.observe(null);
            mWebContentsObserver = null;
        }
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void observeTab(Tab tab) {
        if (mWebContentsObserver != null) {
            mWebContentsObserver.observe(null);
        }
        WebContents webContents = tab.getWebContents();
        if (webContents == null) {
            if (mWebContentsRetries++ < MAX_WEB_CONTENTS_RETRIES) {
                mHandler.postDelayed(
                        () -> {
                            if (!isFinishing()) {
                                observeTab(tab);
                            }
                        },
                        50);
            } else {
                Log.e(TAG, "Timed out waiting for media WebContents");
                Toast.makeText(this, "The media page could not be opened.", Toast.LENGTH_LONG)
                        .show();
                finish();
            }
            return;
        }
        mWebContentsRetries = 0;
        mWebContentsObserver =
                new WebContentsObserver(webContents) {
                    @Override
                    public void didStartNavigationInPrimaryMainFrame(
                            NavigationHandle navigationHandle) {
                        String url = navigationHandle.getUrl().getSpec();
                        if (url == null || url.isEmpty()) {
                            return;
                        }
                        if (mDestination.presentationForUrl(url)
                                == MediaDestination.Presentation.REJECT) {
                            blockNavigation(tab, url);
                        } else {
                            mBlockingNavigation = false;
                        }
                    }

                    @Override
                    public void didRedirectNavigation(NavigationHandle navigationHandle) {
                        String url = navigationHandle.getUrl().getSpec();
                        if (url != null
                                && !url.isEmpty()
                                && mDestination.presentationForUrl(url)
                                        == MediaDestination.Presentation.REJECT) {
                            blockNavigation(tab, url);
                        }
                    }

                    @Override
                    public void didFinishNavigationInPrimaryMainFrame(
                            NavigationHandle navigationHandle) {
                        if (!navigationHandle.hasCommitted() || navigationHandle.isErrorPage()) {
                            return;
                        }
                        updatePresentation(tab, navigationHandle.getUrl().getSpec());
                    }

                    @Override
                    public void primaryMainFrameRenderProcessGone(
                            @TerminationStatus int terminationStatus) {
                        Log.e(TAG, "Media renderer exited with status " + terminationStatus);
                        Toast.makeText(
                                        MediaViewerCustomTabActivity.this,
                                        "The media page stopped unexpectedly.",
                                        Toast.LENGTH_LONG)
                                .show();
                        finish();
                    }
                };
        String currentUrl = tab.getUrl().getSpec();
        if (currentUrl != null && !currentUrl.isEmpty()) {
            updatePresentation(tab, currentUrl);
        }
    }

    private void updatePresentation(Tab tab, String url) {
        MediaDestination.Presentation presentation = mDestination.presentationForUrl(url);
        if (presentation == MediaDestination.Presentation.REJECT) {
            blockNavigation(tab, url);
            return;
        }

        View toolbar = findViewById(R.id.toolbar_container);
        if (toolbar != null) {
            toolbar.setVisibility(
                    presentation == MediaDestination.Presentation.SHOW_ORIGIN
                            ? View.VISIBLE
                            : View.GONE);
        }

        WebContents webContents = tab.getWebContents();
        if (webContents == null) {
            return;
        }
        boolean useDesktopUserAgent = mDestination.usesDesktopUserAgent(url);
        if (TabUtils.isUsingDesktopUserAgent(webContents) != useDesktopUserAgent) {
            TabUtils.switchUserAgent(tab, useDesktopUserAgent);
        }
    }

    private void blockNavigation(Tab tab, String url) {
        if (mBlockingNavigation) {
            return;
        }
        mBlockingNavigation = true;
        Log.w(TAG, "Blocked top-level navigation outside the destination allowlist: " + url);
        mHandler.post(
                () -> {
                    if (isFinishing()) {
                        return;
                    }
                    WebContents webContents = tab.getWebContents();
                    if (webContents != null) {
                        webContents.stop();
                        if (webContents.getNavigationController().canGoBack()) {
                            webContents.getNavigationController().goBack();
                            Toast.makeText(
                                            this,
                                            "Navigation outside this media service was blocked.",
                                            Toast.LENGTH_LONG)
                                    .show();
                            return;
                        }
                    }
                    Toast.makeText(this, "Unsupported media page.", Toast.LENGTH_LONG).show();
                    finish();
                });
    }
}
