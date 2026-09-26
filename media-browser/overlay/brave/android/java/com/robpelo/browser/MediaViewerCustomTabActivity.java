/* Copyright (c) 2026 RobPelo contributors.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at https://mozilla.org/MPL/2.0/. */

package com.robpelo.browser;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.chromium.base.Log;
import org.chromium.base.TerminationStatus;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.customtabs.FullScreenCustomTabActivity;
import org.chromium.chrome.browser.customtabs.content.CustomTabActivityTabProvider;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabUtils;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.NavigationHandle;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.browser.WebContentsObserver;
import org.chromium.ui.modaldialog.ModalDialogManager;
import org.chromium.ui.modelutil.PropertyModel;

/** Chrome-free media surface backed by Brave's normal persistent profile. */
public final class MediaViewerCustomTabActivity extends FullScreenCustomTabActivity {
    private static final String TAG = "RobPeloMedia";
    private static final int MAX_REPEATED_INTENT_TARGETS = 3;
    private static final long REPEATED_INTENT_WINDOW_MS = 5_000;
    private static final int MAX_WEB_CONTENTS_RETRIES = 20;

    private final Handler mMediaHandler = new Handler(Looper.getMainLooper());
    private MediaDestination mDestination;
    private ModalDialogManager mModalDialogManager;
    private ModalDialogManager.ModalDialogManagerObserver mModalDialogObserver;
    private View mVisibleModalDialog;
    private PropertyModel mVisibleModalDialogModel;
    private CustomTabActivityTabProvider.Observer mTabProviderObserver;
    private Tab mCurrentTab;
    private WebContentsObserver mWebContentsObserver;
    private boolean mBlockingNavigation;
    private int mRepeatedIntentTargetCount;
    private String mLastIntentTarget;
    private long mLastIntentTargetAtMs;
    private int mWebContentsRetries;

    @Override
    protected void onPreCreate() {
        mDestination =
                MediaDestination.fromServiceId(
                        getIntent().getStringExtra(MediaViewerActivity.EXTRA_SERVICE));
        super.onPreCreate();
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
        observeModalDialogs();

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

                    @Override
                    public void onAllTabsClosed() {
                        mCurrentTab = null;
                    }
                };
        tabProvider.addObserver(mTabProviderObserver);
        Tab existingTab = tabProvider.getTab();
        if (existingTab != null) {
            observeTab(existingTab);
        }
    }

    @Override
    protected void onDestroyInternal() {
        if (mModalDialogManager != null && mModalDialogObserver != null) {
            mModalDialogManager.removeObserver(mModalDialogObserver);
            mModalDialogObserver = null;
            mModalDialogManager = null;
        }
        hideModalDialog();
        if (mTabProviderObserver != null) {
            getCustomTabActivityTabProvider().removeObserver(mTabProviderObserver);
            mTabProviderObserver = null;
        }
        if (mWebContentsObserver != null) {
            mWebContentsObserver.observe(null);
            mWebContentsObserver = null;
        }
        mMediaHandler.removeCallbacksAndMessages(null);
        super.onDestroyInternal();
    }

    private void observeModalDialogs() {
        mModalDialogManager = getModalDialogManagerSupplier().get();
        if (mModalDialogManager == null) {
            Log.e(TAG, "Modal dialog manager is unavailable");
            return;
        }

        mModalDialogObserver =
                new ModalDialogManager.ModalDialogManagerObserver() {
                    @Override
                    public void onDialogAdded(PropertyModel model) {
                        mVisibleModalDialogModel = model;
                        setToolbarVisible(true);
                    }

                    @Override
                    public void onDialogShown(View dialogView) {
                        showModalDialog(dialogView);
                    }

                    @Override
                    public void onDialogDismissed(PropertyModel model) {
                        if (model == mVisibleModalDialogModel) {
                            hideModalDialog();
                        }
                    }

                    @Override
                    public void onLastDialogDismissed() {
                        restoreToolbarAfterDialog();
                    }
                };
        mModalDialogManager.addObserver(mModalDialogObserver);
    }

    private void showModalDialog(View dialogView) {
        View containerView = findViewById(R.id.tab_modal_dialog_container);
        if (!(containerView instanceof FrameLayout)) {
            Log.e(TAG, "Tab modal dialog container is unavailable");
            return;
        }

        FrameLayout container = (FrameLayout) containerView;
        ViewGroup.LayoutParams containerParams = container.getLayoutParams();
        containerParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        containerParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        container.setLayoutParams(containerParams);

        if (dialogView.getParent() == null) {
            // Zero-height browser controls prevent ChromeTabModalPresenter from ever running its
            // enter animation, so attach the already-created dialog without animation.
            FrameLayout.LayoutParams dialogParams =
                    new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            Gravity.CENTER);
            dialogView.setBackgroundResource(R.drawable.dialog_bg_no_shadow);
            container.addView(dialogView, dialogParams);
        }
        mVisibleModalDialog = dialogView;
        container.setClickable(true);
        container.setAlpha(1f);
        container.setVisibility(View.VISIBLE);
        container.bringToFront();
        container.requestLayout();
    }

    private void hideModalDialog() {
        View containerView = findViewById(R.id.tab_modal_dialog_container);
        if (!(containerView instanceof FrameLayout)) {
            mVisibleModalDialog = null;
            mVisibleModalDialogModel = null;
            return;
        }

        FrameLayout container = (FrameLayout) containerView;
        container.animate().cancel();
        if (mVisibleModalDialog != null && mVisibleModalDialog.getParent() == container) {
            container.removeView(mVisibleModalDialog);
        }
        mVisibleModalDialog = null;
        mVisibleModalDialogModel = null;
        container.setAlpha(1f);
        container.setVisibility(View.GONE);
    }

    private void restoreToolbarAfterDialog() {
        if (isFinishing()
                || mCurrentTab == null
                || mCurrentTab.isDestroyed()
                || !mCurrentTab.isInitialized()) {
            return;
        }
        String url = mCurrentTab.getUrl().getSpec();
        if (url == null || url.isEmpty()) {
            return;
        }
        MediaDestination.Presentation presentation = mDestination.presentationForUrl(url);
        if (presentation != MediaDestination.Presentation.REJECT) {
            setToolbarVisible(presentation == MediaDestination.Presentation.SHOW_ORIGIN);
        }
    }

    private void observeTab(Tab tab) {
        mCurrentTab = tab;
        mBlockingNavigation = false;
        if (mWebContentsObserver != null) {
            mWebContentsObserver.observe(null);
        }
        WebContents webContents = tab.getWebContents();
        if (webContents == null) {
            if (mWebContentsRetries++ < MAX_WEB_CONTENTS_RETRIES) {
                mMediaHandler.postDelayed(
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
                        if (handleIntentNavigation(tab, url)) {
                            return;
                        }
                        if (mBlockingNavigation) {
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
                        if (!navigationHandle.isInPrimaryMainFrame()) {
                            return;
                        }
                        String url = navigationHandle.getUrl().getSpec();
                        if (url != null
                                && !url.isEmpty()
                                && !handleIntentNavigation(tab, url)
                                && !mBlockingNavigation
                                && mDestination.presentationForUrl(url)
                                        == MediaDestination.Presentation.REJECT) {
                            blockNavigation(tab, url);
                        }
                    }

                    private boolean handleIntentNavigation(Tab tab, String url) {
                        if (!url.startsWith("intent:")) {
                            return false;
                        }

                        final String targetUrl;
                        try {
                            targetUrl = Intent.parseUri(url, Intent.URI_INTENT_SCHEME).getDataString();
                        } catch (Exception exception) {
                            Log.e(TAG, "Rejected malformed intent navigation", exception);
                            blockNavigation(tab, url);
                            return true;
                        }
                        if (targetUrl == null
                                || mDestination.presentationForUrl(targetUrl)
                                        == MediaDestination.Presentation.REJECT) {
                            blockNavigation(tab, url);
                            return true;
                        }

                        long now = SystemClock.elapsedRealtime();
                        if (targetUrl.equals(mLastIntentTarget)
                                && now - mLastIntentTargetAtMs <= REPEATED_INTENT_WINDOW_MS) {
                            mRepeatedIntentTargetCount++;
                        } else {
                            mLastIntentTarget = targetUrl;
                            mRepeatedIntentTargetCount = 1;
                        }
                        mLastIntentTargetAtMs = now;
                        if (mRepeatedIntentTargetCount > MAX_REPEATED_INTENT_TARGETS) {
                            Log.e(TAG, "Rejected repeated external-app navigation loop");
                            blockNavigation(tab, url);
                            return true;
                        }

                        mBlockingNavigation = true;
                        mMediaHandler.post(
                                () -> {
                                    try {
                                        if (isFinishing() || tab.isDestroyed()) {
                                            return;
                                        }
                                        WebContents webContents = tab.getWebContents();
                                        if (webContents == null) {
                                            return;
                                        }
                                        Log.i(TAG, "Keeping external-app navigation in the media browser");
                                        webContents.stop();
                                        tab.loadUrl(new LoadUrlParams(targetUrl));
                                    } finally {
                                        mBlockingNavigation = false;
                                    }
                                });
                        return true;
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

        setToolbarVisible(presentation == MediaDestination.Presentation.SHOW_ORIGIN);

        WebContents webContents = tab.getWebContents();
        if (webContents == null) {
            return;
        }
        boolean useDesktopUserAgent = mDestination.usesDesktopUserAgent(url);
        if (TabUtils.isUsingDesktopUserAgent(webContents) != useDesktopUserAgent) {
            TabUtils.switchUserAgent(tab, useDesktopUserAgent);
        }
    }

    private void setToolbarVisible(boolean visible) {
        View toolbar = findViewById(R.id.toolbar_container);
        if (toolbar != null) {
            toolbar.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void blockNavigation(Tab tab, String url) {
        if (mBlockingNavigation) {
            return;
        }
        mBlockingNavigation = true;
        Log.w(TAG, "Blocked top-level navigation outside the destination allowlist: " + url);
        mMediaHandler.post(
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
