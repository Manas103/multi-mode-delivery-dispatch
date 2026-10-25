package com.doordashsim.dispatch

import kotlin.random.Random

/**
 * Deterministic synthetic order trace, seeded so the same seed always reproduces the same trace.
 * Payload is exponentially distributed around small package sizes; distance is uniform over a
 * plausible last-mile range; the remaining fields are independent Bernoulli draws tuned to
 * plausible real-world rates (occasional rain, most but not all geofences cleared, merchants
 * usually but not always ready at order time).
 */
object OrderTraceGenerator {
    fun generate(count: Int, seed: Long = 42L): List<OrderConditions> {
        val random = Random(seed)
        return (0 until count).map { i ->
            OrderConditions(
                orderId = "order-$i",
                payloadKg = -2.0 * kotlin.math.ln(1.0 - random.nextDouble()).coerceAtMost(8.0),
                distanceKm = 0.3 + random.nextDouble() * 9.7,
                windSpeedKph = random.nextDouble() * 40.0,
                precipitation = random.nextDouble() < 0.15,
                geofenceClearedRobot = random.nextDouble() < 0.70,
                geofenceClearedDrone = random.nextDouble() < 0.70,
                merchantReady = random.nextDouble() < 0.95
            )
        }
    }
}
