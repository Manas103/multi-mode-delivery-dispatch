package com.doordashsim.dispatch

import com.doordashsim.dispatch.proto.AssignmentResponse
import com.doordashsim.dispatch.proto.DispatchServiceGrpcKt
import com.doordashsim.dispatch.proto.HandoffAck
import com.doordashsim.dispatch.proto.HandoffEvent
import com.doordashsim.dispatch.proto.ModeScore
import com.doordashsim.dispatch.proto.OrderRequest
import com.doordashsim.dispatch.proto.Mode as ProtoMode

fun DeliveryMode.toProto(): ProtoMode = when (this) {
    DeliveryMode.COURIER -> ProtoMode.COURIER
    DeliveryMode.SIDEWALK_ROBOT -> ProtoMode.SIDEWALK_ROBOT
    DeliveryMode.DRONE -> ProtoMode.DRONE
}

class DispatchGrpcService(private val ledger: HandoffLedger) : DispatchServiceGrpcKt.DispatchServiceCoroutineImplBase() {

    override suspend fun assignOrder(request: OrderRequest): AssignmentResponse {
        val conditions = OrderConditions(
            orderId = request.orderId,
            payloadKg = request.payloadKg,
            distanceKm = request.distanceKm,
            windSpeedKph = request.windSpeedKph,
            precipitation = request.precipitation,
            geofenceClearedRobot = request.geofenceClearedRobot,
            geofenceClearedDrone = request.geofenceClearedDrone,
            merchantReady = request.merchantReady
        )
        val assignment = Dispatcher.assign(conditions)
        return AssignmentResponse.newBuilder()
            .setOrderId(assignment.orderId)
            .setAssignedMode(assignment.assignedMode.toProto())
            .setCostCents(assignment.costCents)
            .addAllScores(
                assignment.scores.map {
                    ModeScore.newBuilder()
                        .setMode(it.mode.toProto())
                        .setEligible(it.eligible)
                        .setIneligibleReason(it.reason)
                        .setCostCents(it.costCents)
                        .build()
                }
            )
            .build()
    }

    override suspend fun applyHandoff(request: HandoffEvent): HandoffAck {
        val result = ledger.apply(request.orderId, request.sequence, request.state, request.handoffId)
        return HandoffAck.newBuilder()
            .setHandoffId(request.handoffId)
            .setApplied(result.applied)
            .setResultingState(result.resultingState)
            .build()
    }
}
