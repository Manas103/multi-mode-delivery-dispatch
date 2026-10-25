package com.doordashsim.dispatch

fun main() {
    val port = System.getenv("DISPATCH_PORT")?.toIntOrNull() ?: 0
    val jdbcUrl = System.getenv("DISPATCH_PG_URL") ?: "jdbc:postgresql://localhost:54329/dispatch"
    val user = System.getenv("DISPATCH_PG_USER") ?: "dispatch"
    val password = System.getenv("DISPATCH_PG_PASSWORD") ?: "dispatch"

    val dataSource = Db.dataSource(jdbcUrl, user, password)
    val server = DispatchGrpcServer(port, dataSource)
    val boundPort = server.start()
    println("DISPATCH_SERVER_LISTENING_ON_PORT=$boundPort")
    Runtime.getRuntime().addShutdownHook(Thread { server.stop() })
    server.awaitTermination()
}
