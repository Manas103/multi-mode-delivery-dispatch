package com.doordashsim.dispatch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EligibilityTest {

    private fun base(orderId: String = "o1") = OrderConditions(
        orderId = orderId,
        payloadKg = 1.0,
        distanceKm = 1.0,
        windSpeedKph = 5.0,
        precipitation = false,
        geofenceClearedRobot = true,
        geofenceClearedDrone = true,
        merchantReady = true
    )

    @Test
    fun `all three modes eligible for a light nearby clear order`() {
        val scores = Eligibility.score(base())
        assertTrue(scores.all { it.eligible })
    }

    @Test
    fun `heavy order only courier eligible`() {
        val scores = Eligibility.score(base().copy(payloadKg = 10.0))
        val byMode = scores.associateBy { it.mode }
        assertTrue(byMode[DeliveryMode.COURIER]!!.eligible)
        assertFalse(byMode[DeliveryMode.SIDEWALK_ROBOT]!!.eligible)
        assertFalse(byMode[DeliveryMode.DRONE]!!.eligible)
    }

    @Test
    fun `precipitation grounds robot and drone but not courier`() {
        val scores = Eligibility.score(base().copy(precipitation = true))
        val byMode = scores.associateBy { it.mode }
        assertTrue(byMode[DeliveryMode.COURIER]!!.eligible)
        assertFalse(byMode[DeliveryMode.SIDEWALK_ROBOT]!!.eligible)
        assertFalse(byMode[DeliveryMode.DRONE]!!.eligible)
    }

    @Test
    fun `distance beyond robot range but within drone range disqualifies only robot`() {
        val scores = Eligibility.score(base().copy(distanceKm = 5.0))
        val byMode = scores.associateBy { it.mode }
        assertFalse(byMode[DeliveryMode.SIDEWALK_ROBOT]!!.eligible)
        assertTrue(byMode[DeliveryMode.DRONE]!!.eligible)
        assertTrue(byMode[DeliveryMode.COURIER]!!.eligible)
    }

    @Test
    fun `unready merchant disqualifies robot and drone, not courier`() {
        val scores = Eligibility.score(base().copy(merchantReady = false))
        val byMode = scores.associateBy { it.mode }
        assertTrue(byMode[DeliveryMode.COURIER]!!.eligible)
        assertFalse(byMode[DeliveryMode.SIDEWALK_ROBOT]!!.eligible)
        assertFalse(byMode[DeliveryMode.DRONE]!!.eligible)
    }

    @Test
    fun `uncleared geofence disqualifies only the matching mode`() {
        val scores = Eligibility.score(base().copy(geofenceClearedRobot = false))
        val byMode = scores.associateBy { it.mode }
        assertFalse(byMode[DeliveryMode.SIDEWALK_ROBOT]!!.eligible)
        assertTrue(byMode[DeliveryMode.DRONE]!!.eligible)
    }

    @Test
    fun `moderate wind grounds robot before drone`() {
        val scores = Eligibility.score(base().copy(windSpeedKph = 27.0))
        val byMode = scores.associateBy { it.mode }
        assertFalse(byMode[DeliveryMode.SIDEWALK_ROBOT]!!.eligible)
        assertTrue(byMode[DeliveryMode.DRONE]!!.eligible)
    }

    @Test
    fun `payload over courier cap is ineligible everywhere`() {
        val scores = Eligibility.score(base().copy(payloadKg = 20.0))
        assertTrue(scores.none { it.eligible })
    }

    @Test
    fun `dispatcher always falls back to courier when nothing else is eligible`() {
        val assignment = Dispatcher.assign(base().copy(precipitation = true, distanceKm = 5.0))
        assertEquals(DeliveryMode.COURIER, assignment.assignedMode)
    }

    @Test
    fun `dispatcher picks the cheapest eligible mode, not just the first eligible one`() {
        val assignment = Dispatcher.assign(base())
        val cheapest = assignment.scores.filter { it.eligible }.minByOrNull { it.costCents }!!
        assertEquals(cheapest.mode, assignment.assignedMode)
        assertEquals(cheapest.costCents, assignment.costCents)
    }
}
