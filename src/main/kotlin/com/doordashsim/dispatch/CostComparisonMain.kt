package com.doordashsim.dispatch

/**
 * Standalone measurement: replays a synthetic order trace through the multi-mode dispatcher and
 * through a forced courier-only baseline on the exact same trace, then reports the cost gap.
 * Usage: gradle run -PmainClass=com.doordashsim.dispatch.CostComparisonMainKt
 */
fun main(args: Array<String>) {
    val count = args.getOrNull(0)?.toIntOrNull() ?: 250_000
    val trace = OrderTraceGenerator.generate(count)

    var dispatcherCostCents = 0.0
    var courierOnlyCostCents = 0.0
    val modeCounts = mutableMapOf<DeliveryMode, Int>()

    for (order in trace) {
        val assignment = Dispatcher.assign(order)
        dispatcherCostCents += assignment.costCents
        courierOnlyCostCents += Dispatcher.courierOnlyCostCents(order)
        modeCounts[assignment.assignedMode] = (modeCounts[assignment.assignedMode] ?: 0) + 1
    }

    val savingsPct = (courierOnlyCostCents - dispatcherCostCents) / courierOnlyCostCents * 100.0

    println("orders_replayed=$count")
    println("mode_counts=$modeCounts")
    println("dispatcher_total_cost_cents=$dispatcherCostCents")
    println("courier_only_total_cost_cents=$courierOnlyCostCents")
    println("cost_savings_pct=%.4f".format(savingsPct))
}
