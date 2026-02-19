package com.polecatworks.kotlin.k8smicro.health

import com.polecatworks.kotlin.k8smicro.app.AppService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

/**
 * Configures routing for the health service.
 *
 * Defines endpoints for version, startup, stop, metrics, readiness, and liveness checks.
 *
 * @param version The application version.
 * @param appService Reference to the main application service.
 * @param appMicrometerRegistry Registry for Prometheus metrics.
 * @param health The health system to query status from.
 * @param healthService Reference to the health service itself.
 */
@OptIn(ExperimentalTime::class)
fun Application.configureHealthRouting(
    version: String,
    appService: AppService,
    appMicrometerRegistry: PrometheusMeterRegistry,
    health: HealthSystem,
    healthService: HealthService,
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
                healthService.stop()
                call.respondText { "Shutdown initiated" }
            }
            get("/metrics") {
                // Adding prometheus: https://ktor.io/docs/micrometer-metrics.html#install_plugin
                call.respond(appMicrometerRegistry.scrape())
            }
            get("/ready") {
//            call.respond(health)

                val now = TimeSource.Monotonic.markNow()
                val myReady = health.checkReady(now)
                call.response.status(if (myReady.valid) HttpStatusCode.OK else HttpStatusCode.TooManyRequests)
                call.application.environment.log
                    .debug("Ready check: $myReady")
                call.respond(myReady)
//            call.respondText { "ready" }
            }
            get("/alive") {
                val now = TimeSource.Monotonic.markNow()
                val myReady = health.checkAlive(now)
                call.response.status(if (myReady.valid) HttpStatusCode.OK else HttpStatusCode.NotAcceptable)
                call.application.environment.log
                    .debug("Alive check: $myReady")
                call.respond(myReady)
            }
        }
    }
}
