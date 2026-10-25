package com.doordashsim.dispatch

import java.sql.Connection
import javax.sql.DataSource

data class HandoffApplyResult(val applied: Boolean, val resultingState: String)

/**
 * Durable, idempotent ledger of applied handoff transitions. The unique constraint on
 * (order_id, sequence) is what makes a redelivered Kafka message a no-op instead of a duplicate
 * state advance: a second INSERT for the same (order_id, sequence) always loses the race to the
 * database, never to application-level logic, so it is correct even if two consumer instances
 * process the same redelivered message concurrently.
 */
class HandoffLedger(private val dataSource: DataSource) {

    fun init() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { st ->
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS handoff_log (
                        order_id TEXT NOT NULL,
                        sequence BIGINT NOT NULL,
                        state TEXT NOT NULL,
                        handoff_id TEXT NOT NULL,
                        applied_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                        PRIMARY KEY (order_id, sequence)
                    )
                    """.trimIndent()
                )
            }
        }
    }

    fun apply(orderId: String, sequence: Long, state: String, handoffId: String): HandoffApplyResult {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                val inserted = insertIfAbsent(conn, orderId, sequence, state, handoffId)
                val currentMax = maxSequence(conn, orderId)
                conn.commit()
                val resultingState = HandoffState.forSequence(currentMax)?.name ?: "UNKNOWN"
                return HandoffApplyResult(applied = inserted, resultingState = resultingState)
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
    }

    private fun insertIfAbsent(conn: Connection, orderId: String, sequence: Long, state: String, handoffId: String): Boolean {
        conn.prepareStatement(
            "INSERT INTO handoff_log (order_id, sequence, state, handoff_id) VALUES (?, ?, ?, ?) ON CONFLICT (order_id, sequence) DO NOTHING"
        ).use { ps ->
            ps.setString(1, orderId)
            ps.setLong(2, sequence)
            ps.setString(3, state)
            ps.setString(4, handoffId)
            val rowsAffected = ps.executeUpdate()
            return rowsAffected == 1
        }
    }

    private fun maxSequence(conn: Connection, orderId: String): Long {
        conn.prepareStatement("SELECT COALESCE(MAX(sequence), 0) FROM handoff_log WHERE order_id = ?").use { ps ->
            ps.setString(1, orderId)
            ps.executeQuery().use { rs ->
                rs.next()
                return rs.getLong(1)
            }
        }
    }

    fun countRowsForOrder(orderId: String): Int {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM handoff_log WHERE order_id = ?").use { ps ->
                ps.setString(1, orderId)
                ps.executeQuery().use { rs ->
                    rs.next()
                    return rs.getInt(1)
                }
            }
        }
    }

    fun totalRows(): Long {
        dataSource.connection.use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery("SELECT COUNT(*) FROM handoff_log").use { rs ->
                    rs.next()
                    return rs.getLong(1)
                }
            }
        }
    }
}
