package com.doordashsim.dispatch

import com.doordashsim.dispatch.proto.DispatchServiceGrpcKt
import com.doordashsim.dispatch.proto.OrderRequest
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import kotlin.system.measureNanoTime

/**
 * Starts the real dispatch gRPC server on a loopback port, then replays a synthetic order trace
 * against it over real gRPC calls using a fixed pool of WORKER_THREADS concurrent callers,
 * measuring p50/p99 wall-clock latency per call. Requires DISPATCH_PG_URL/USER/PASSWORD pointing
 * at a reachable Postgres (the server's ledger table must initialize even though AssignOrder
 * itself never touches the database).
 */
const val WORKER_THREADS = 8

fun main(args: Array<String>) {
    val count = args.getOrNull(0)?.toIntOrNull() ?: 250_000
    val jdbcUrl = System.getenv("DISPATCH_PG_URL") ?: "jdbc:postgresql://localhost:54329/dispatch"
    val user = System.getenv("DISPATCH_PG_USER") ?: "dispatch"
    val password = System.getenv("DISPATCH_PG_PASSWORD") ?: "dispatch"

    val dataSource = Db.dataSource(jdbcUrl, user, password)
    val server = DispatchGrpcServer(0, dataSource)
    val port = server.start()
    println("load_benchmark_server_port=$port")

    val channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build()
    val pool = Executors.newFixedThreadPool(WORKER_THREADS)
    val dispatcher = pool.asCoroutineDispatcher()

    val trace = OrderTraceGenerator.generate(count, seed = 123L)
    val latenciesNanos = LongArray(count)

    val totalWallNanos = measureNanoTime {
        runBlocking(dispatcher) {
            val stub = DispatchServiceGrpcKt.DispatchServiceCoroutineStub(channel)
            val chunkSize = (count + WORKER_THREADS - 1) / WORKER_THREADS
            val jobs = (0 until WORKER_THREADS).map { worker ->
                async(dispatcher) {
                    val start = worker * chunkSize
                    val end = minOf(start + chunkSize, count)
                    for (i in start until end) {
                        val o = trace[i]
                        val req = OrderRequest.newBuilder()
                            .setOrderId(o.orderId)
                            .setPayloadKg(o.payloadKg)
                            .setDistanceKm(o.distanceKm)
                            .setWindSpeedKph(o.windSpeedKph)
                            .setPrecipitation(o.precipitation)
                            .setGeofenceClearedRobot(o.geofenceClearedRobot)
                            .setGeofenceClearedDrone(o.geofenceClearedDrone)
                            .setMerchantReady(o.merchantReady)
                            .build()
                        val t0 = System.nanoTime()
                        stub.assignOrder(req)
                        latenciesNanos[i] = System.nanoTime() - t0
                    }
                }
            }
            jobs.awaitAll()
        }
    }

    channel.shutdownNow()
    pool.shutdown()
    server.stop()

    latenciesNanos.sort()
    val p50 = latenciesNanos[(count * 0.50).toInt().coerceAtMost(count - 1)] / 1_000_000.0
    val p99 = latenciesNanos[(count * 0.99).toInt().coerceAtMost(count - 1)] / 1_000_000.0
    val mean = latenciesNanos.average() / 1_000_000.0
    val throughput = count / (totalWallNanos / 1e9)

    println("orders_replayed=$count")
    println("worker_threads=$WORKER_THREADS")
    println("mean_latency_ms=%.4f".format(mean))
    println("p50_latency_ms=%.4f".format(p50))
    println("p99_latency_ms=%.4f".format(p99))
    println("throughput_orders_per_sec=%.2f".format(throughput))
}
