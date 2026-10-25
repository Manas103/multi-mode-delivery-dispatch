package com.doordashsim.dispatch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CostModelTest {
    @Test
    fun `courier is more expensive per km than robot and drone`() {
        val d = 4.0
        assertTrue(CostModel.costCents(DeliveryMode.COURIER, d) > CostModel.costCents(DeliveryMode.SIDEWALK_ROBOT, d))
        assertTrue(CostModel.costCents(DeliveryMode.COURIER, d) > CostModel.costCents(DeliveryMode.DRONE, d))
    }

    @Test
    fun `cost is linear in distance with the configured fixed and per-km rates`() {
        assertEquals(
            CostModel.COURIER_FIXED_CENTS + CostModel.COURIER_PER_KM_CENTS * 2.0,
            CostModel.costCents(DeliveryMode.COURIER, 2.0)
        )
        assertEquals(
            CostModel.DRONE_FIXED_CENTS + CostModel.DRONE_PER_KM_CENTS * 7.0,
            CostModel.costCents(DeliveryMode.DRONE, 7.0)
        )
    }

    @Test
    fun `zero distance still charges the fixed cost`() {
        assertEquals(CostModel.ROBOT_FIXED_CENTS, CostModel.costCents(DeliveryMode.SIDEWALK_ROBOT, 0.0))
    }
}
