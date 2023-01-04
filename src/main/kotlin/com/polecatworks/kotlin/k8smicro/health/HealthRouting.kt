package com.polecatworks.kotlin.k8smicro.health

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

@OptIn(ExperimentalTime::class)
fun Application.configureHealthRouting(
    health: HealthSystem,
    appMicrometerRegistry: PrometheusMeterRegistry,
    version: String,
    healthService: HealthService
) {
    routing {
        route("/hams") {
            get("/version") {
                call.respondText { version }
            }
            get("/startup") {
                // Simple probe that confirms the web service is running
                call.respondText { "startup good" }
            }
            get("/stop") {
                // TODO: Implement this using running. How to propagate to application service
                healthService.stop()
                call.respondText { "Shutdown initiated" }
            }
            get("/metrics") {
                // Adding prometheus: https://ktor.io/docs/micrometer-metrics.html#install_plugin
                call.respond(appMicrometerRegistry.scrape())
            }
            get("/ready") {
//            call.respond(health)
                call.application.environment.log.info("Ready check")
                val now = TimeSource.Monotonic.markNow()
                val myReady = health.checkReady(now)
                call.respond(myReady)
//            call.respondText { "ready" }
            }
            get("/alive") {
                call.application.environment.log.info("Alive check")
                val now = TimeSource.Monotonic.markNow()
                val myReady = health.checkAlive(now)
                call.respond(myReady)
            }
        }
    }
}
