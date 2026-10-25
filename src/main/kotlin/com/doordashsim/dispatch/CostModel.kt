package com.doordashsim.dispatch

/**
 * Per-mode cost in cents: a fixed dispatch cost plus a per-kilometer rate. Robot and drone have
 * lower marginal cost per kilometer than courier but a non-zero fixed cost (robot staging, drone
 * launch and airspace clearance), so they only win on the orders their eligibility envelope allows.
 */
object CostModel {
    const val COURIER_FIXED_CENTS = 350.0
    const val COURIER_PER_KM_CENTS = 45.0
    const val ROBOT_FIXED_CENTS = 120.0
    const val ROBOT_PER_KM_CENTS = 20.0
    const val DRONE_FIXED_CENTS = 200.0
    const val DRONE_PER_KM_CENTS = 15.0

    fun costCents(mode: DeliveryMode, distanceKm: Double): Double = when (mode) {
        DeliveryMode.COURIER -> COURIER_FIXED_CENTS + COURIER_PER_KM_CENTS * distanceKm
        DeliveryMode.SIDEWALK_ROBOT -> ROBOT_FIXED_CENTS + ROBOT_PER_KM_CENTS * distanceKm
        DeliveryMode.DRONE -> DRONE_FIXED_CENTS + DRONE_PER_KM_CENTS * distanceKm
    }
}
