import com.google.protobuf.gradle.id

plugins {
    kotlin("jvm") version "1.9.24"
    id("com.google.protobuf") version "0.9.4"
    application
}

repositories {
    mavenCentral()
}

val grpcVersion = "1.65.1"
val grpcKotlinVersion = "1.4.1"
val protobufVersion = "3.25.3"

dependencies {
    implementation("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-netty:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("com.google.protobuf:protobuf-kotlin:$protobufVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("org.apache.kafka:kafka-clients:3.7.1")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.slf4j:slf4j-simple:2.0.13")
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("io.grpc:grpc-testing:$grpcVersion")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("com.doordashsim.dispatch.MainKt")
}

sourceSets {
    main {
        proto {
            srcDir("src/main/proto")
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:$grpcKotlinVersion:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
                id("grpckt")
            }
            task.builtins {
                id("kotlin")
            }
        }
    }
}

tasks.withType<Copy> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register<JavaExec>("costComparison") {
    group = "benchmark"
    description = "Replays a synthetic order trace through the dispatcher and a courier-only baseline, reporting the cost gap."
    mainClass.set("com.doordashsim.dispatch.CostComparisonMainKt")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(project.findProperty("orderCount")?.toString() ?: "250000")
}

tasks.register<JavaExec>("loadBenchmark") {
    group = "benchmark"
    description = "Starts the real dispatch gRPC server and replays a synthetic order trace against it, measuring p50/p99 latency."
    mainClass.set("com.doordashsim.dispatch.LoadBenchmarkMainKt")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(project.findProperty("orderCount")?.toString() ?: "250000")
}

tasks.register<JavaExec>("idempotencyStress") {
    group = "benchmark"
    description = "Produces, consumes, then forces redelivery of 50,000 handoff events through real Kafka and Postgres."
    mainClass.set("com.doordashsim.dispatch.IdempotencyStressMainKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 1
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
