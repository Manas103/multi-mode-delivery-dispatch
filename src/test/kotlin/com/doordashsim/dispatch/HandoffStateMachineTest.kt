package com.doordashsim.dispatch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandoffStateMachineTest {
    @Test
    fun `sequence one after zero is the next step`() {
        assertTrue(HandoffStateMachine.isNextStep(0, 1))
    }

    @Test
    fun `repeating the same sequence is not a next step`() {
        assertFalse(HandoffStateMachine.isNextStep(3, 3))
    }

    @Test
    fun `skipping a sequence is not a next step`() {
        assertFalse(HandoffStateMachine.isNextStep(3, 5))
    }

    @Test
    fun `going backwards is not a next step`() {
        assertFalse(HandoffStateMachine.isNextStep(5, 3))
    }

    @Test
    fun `all six states map to their documented sequence number`() {
        assertEquals(HandoffState.CREATED, HandoffState.forSequence(1))
        assertEquals(HandoffState.NOTIFIED, HandoffState.forSequence(2))
        assertEquals(HandoffState.MERCHANT_ACK, HandoffState.forSequence(3))
        assertEquals(HandoffState.READY_FOR_PICKUP, HandoffState.forSequence(4))
        assertEquals(HandoffState.HANDED_OFF, HandoffState.forSequence(5))
        assertEquals(HandoffState.COMPLETED, HandoffState.forSequence(6))
        assertEquals(6, HandoffState.entries.size)
    }
}
