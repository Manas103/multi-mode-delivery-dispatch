package com.doordashsim.dispatch

import io.grpc.Server
import io.grpc.ServerBuilder
import javax.sql.DataSource

class DispatchGrpcServer(private val port: Int, dataSource: DataSource) {
    private val ledger = HandoffLedger(dataSource)
    private var server: Server? = null

    fun start(): Int {
        ledger.init()
        val builder = ServerBuilder.forPort(port)
            .addService(DispatchGrpcService(ledger).bindService())
        val s = builder.build().start()
        server = s
        return s.port
    }

    fun awaitTermination() {
        server?.awaitTermination()
    }

    fun stop() {
        server?.shutdown()
    }
}
