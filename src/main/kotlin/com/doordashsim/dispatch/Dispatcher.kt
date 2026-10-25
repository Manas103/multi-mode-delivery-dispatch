package com.doordashsim.dispatch

data class ScoredMode(val mode: DeliveryMode, val eligible: Boolean, val reason: String, val costCents: Double)

data class Assignment(val orderId: String, val assignedMode: DeliveryMode, val costCents: Double, val scores: List<ScoredMode>)

/**
 * Picks the cheapest eligible mode. Courier is always eligible under its payload cap, so it is
 * the guaranteed fallback when robot and drone are both ruled out; the dispatcher never returns
 * "no mode available".
 */
object Dispatcher {
    fun assign(order: OrderConditions): Assignment {
        val scored = Eligibility.score(order).map {
            ScoredMode(it.mode, it.eligible, it.reason, CostModel.costCents(it.mode, order.distanceKm))
        }
        val chosen = scored.filter { it.eligible }.minByOrNull { it.costCents }
            ?: scored.first { it.mode == DeliveryMode.COURIER }
        return Assignment(order.orderId, chosen.mode, chosen.costCents, scored)
    }

    /** Cost if every order were forced onto courier, regardless of what it is actually eligible for. */
    fun courierOnlyCostCents(order: OrderConditions): Double = CostModel.costCents(DeliveryMode.COURIER, order.distanceKm)
}
