package com.doordashsim.dispatch

/** A single order's physical and operational conditions, independent of the proto wire type. */
data class OrderConditions(
    val orderId: String,
    val payloadKg: Double,
    val distanceKm: Double,
    val windSpeedKph: Double,
    val precipitation: Boolean,
    val geofenceClearedRobot: Boolean,
    val geofenceClearedDrone: Boolean,
    val merchantReady: Boolean
)

enum class DeliveryMode { COURIER, SIDEWALK_ROBOT, DRONE }

data class ModeEligibility(val mode: DeliveryMode, val eligible: Boolean, val reason: String)

/**
 * Per-mode eligibility rules. Courier is the fallback mode with the widest envelope (a human can
 * carry more, in worse weather, without a cleared geofence). Robot and drone are each gated on
 * payload, distance range, weather and a cleared geofence, and both additionally require merchant
 * readiness because only courier can wait curbside for a merchant that is not yet ready.
 */
object Eligibility {
    const val COURIER_MAX_PAYLOAD_KG = 15.0
    const val ROBOT_MAX_PAYLOAD_KG = 4.5
    const val ROBOT_MAX_DISTANCE_KM = 3.0
    const val ROBOT_MAX_WIND_KPH = 25.0
    const val DRONE_MAX_PAYLOAD_KG = 2.5
    const val DRONE_MAX_DISTANCE_KM = 8.0
    const val DRONE_MAX_WIND_KPH = 30.0

    fun score(order: OrderConditions): List<ModeEligibility> = listOf(
        scoreCourier(order),
        scoreRobot(order),
        scoreDrone(order)
    )

    private fun scoreCourier(o: OrderConditions): ModeEligibility =
        if (o.payloadKg <= COURIER_MAX_PAYLOAD_KG) {
            ModeEligibility(DeliveryMode.COURIER, true, "")
        } else {
            ModeEligibility(DeliveryMode.COURIER, false, "payload exceeds courier capacity")
        }

    private fun scoreRobot(o: OrderConditions): ModeEligibility {
        if (!o.merchantReady) return ModeEligibility(DeliveryMode.SIDEWALK_ROBOT, false, "merchant not ready")
        if (!o.geofenceClearedRobot) return ModeEligibility(DeliveryMode.SIDEWALK_ROBOT, false, "geofence not cleared for robot")
        if (o.payloadKg > ROBOT_MAX_PAYLOAD_KG) return ModeEligibility(DeliveryMode.SIDEWALK_ROBOT, false, "payload exceeds robot capacity")
        if (o.distanceKm > ROBOT_MAX_DISTANCE_KM) return ModeEligibility(DeliveryMode.SIDEWALK_ROBOT, false, "distance exceeds robot range")
        if (o.precipitation) return ModeEligibility(DeliveryMode.SIDEWALK_ROBOT, false, "precipitation")
        if (o.windSpeedKph > ROBOT_MAX_WIND_KPH) return ModeEligibility(DeliveryMode.SIDEWALK_ROBOT, false, "wind exceeds robot limit")
        return ModeEligibility(DeliveryMode.SIDEWALK_ROBOT, true, "")
    }

    private fun scoreDrone(o: OrderConditions): ModeEligibility {
        if (!o.merchantReady) return ModeEligibility(DeliveryMode.DRONE, false, "merchant not ready")
        if (!o.geofenceClearedDrone) return ModeEligibility(DeliveryMode.DRONE, false, "geofence not cleared for drone")
        if (o.payloadKg > DRONE_MAX_PAYLOAD_KG) return ModeEligibility(DeliveryMode.DRONE, false, "payload exceeds drone capacity")
        if (o.distanceKm > DRONE_MAX_DISTANCE_KM) return ModeEligibility(DeliveryMode.DRONE, false, "distance exceeds drone range")
        if (o.precipitation) return ModeEligibility(DeliveryMode.DRONE, false, "precipitation")
        if (o.windSpeedKph > DRONE_MAX_WIND_KPH) return ModeEligibility(DeliveryMode.DRONE, false, "wind exceeds drone limit")
        return ModeEligibility(DeliveryMode.DRONE, true, "")
    }
}
