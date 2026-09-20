// Adapted from OpenRide commit a8b2aceae722c3fb2d5159f0a6389ba3cadd4725.
// Method order defines Binder transaction codes and must not be changed.
// Licensed under Apache License 2.0; see third_party/openride/.
package com.onepeloton.affernetservice;

import com.onepeloton.affernetservice.IV1Callback;

interface IV1Interface {
    void registerCallback(IV1Callback callback, String identifier);
    void unregisterCallback(IV1Callback callback, String identifier);
    boolean setFakeDataMode(boolean enabled);
    int setCallbackReportRate(int rateMillis);
}

