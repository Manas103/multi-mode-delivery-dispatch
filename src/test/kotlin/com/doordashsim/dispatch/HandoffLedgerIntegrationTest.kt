package com.doordashsim.dispatch

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Requires a real reachable Postgres (see README "Building and running"). Skips cleanly, rather
 * than failing, if DISPATCH_PG_URL is unset or the database cannot be reached, following this
 * portfolio's model-validation-alerting precedent for services that need infrastructure.
 */
class HandoffLedgerIntegrationTest {

    private fun reachable(): Boolean = try {
        DriverManager.getConnection(jdbcUrl(), "dispatch", "dispatch").use { true }
    } catch (e: Exception) {
        false
    }

    private fun jdbcUrl() = System.getenv("DISPATCH_PG_URL") ?: "jdbc:postgresql://localhost:54329/dispatch"

    private lateinit var ledger: HandoffLedger

    @BeforeEach
    fun setUp() {
        assumeTrue(reachable(), "Postgres not reachable at ${jdbcUrl()}; skipping integration test")
        val ds = Db.dataSource(jdbcUrl(), "dispatch", "dispatch")
        ledger = HandoffLedger(ds)
        ledger.init()
    }

    @Test
    fun `first application of a sequence is applied, a repeat of the same sequence is a duplicate`() {
        val orderId = "itest-" + UUID.randomUUID()
        val first = ledger.apply(orderId, 1, "CREATED", "h1")
        val duplicate = ledger.apply(orderId, 1, "CREATED", "h1-redelivered")
        assertTrue(first.applied)
        assertFalse(duplicate.applied)
        assertEquals("CREATED", first.resultingState)
        assertEquals(1, ledger.countRowsForOrder(orderId))
    }

    @Test
    fun `six sequential handoffs reach COMPLETED with exactly six rows`() {
        val orderId = "itest-" + UUID.randomUUID()
        val states = listOf("CREATED", "NOTIFIED", "MERCHANT_ACK", "READY_FOR_PICKUP", "HANDED_OFF", "COMPLETED")
        states.forEachIndexed { idx, state ->
            val result = ledger.apply(orderId, (idx + 1).toLong(), state, "h-$idx")
            assertTrue(result.applied)
        }
        assertEquals(6, ledger.countRowsForOrder(orderId))
        val replay = ledger.apply(orderId, 6, "COMPLETED", "h-replayed")
        assertFalse(replay.applied)
        assertEquals("COMPLETED", replay.resultingState)
    }
}
