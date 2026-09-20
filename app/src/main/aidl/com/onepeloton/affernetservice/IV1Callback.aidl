// Adapted from OpenRide commit a8b2aceae722c3fb2d5159f0a6389ba3cadd4725.
// Method order defines Binder transaction codes and must not be changed.
// Licensed under Apache License 2.0; see third_party/openride/.
package com.onepeloton.affernetservice;

import com.onepeloton.affernetservice.BikeData;

oneway interface IV1Callback {
    void onSensorDataChange(in BikeData bikeData);
    void onSensorError(long errorCode);
    void onCalibrationStatus(int status, boolean success, long timestamp);
}

