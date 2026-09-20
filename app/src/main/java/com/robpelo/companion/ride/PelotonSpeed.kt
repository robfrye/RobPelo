/*
 * Modified from OpenRide commit a8b2aceae722c3fb2d5159f0a6389ba3cadd4725.
 * Copyright 2026 The OpenRide Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.robpelo.companion.ride

import kotlin.math.sqrt

internal fun pelotonSpeedMphFromPower(powerWatts: Double): Double {
    if (powerWatts < 0.1) {
        return 0.0
    }

    val rootPower = sqrt(powerWatts)
    return if (powerWatts < 26.0) {
        0.057 -
            0.172 * rootPower +
            0.759 * rootPower * rootPower -
            0.079 * rootPower * rootPower * rootPower
    } else {
        -1.635 +
            2.325 * rootPower -
            0.064 * rootPower * rootPower +
            0.001 * rootPower * rootPower * rootPower
    }.coerceAtLeast(0.0)
}

