package com.polecatworks.kotlin.k8smicro.plugins

import com.polecatworks.kotlin.k8smicro.HealthSystem
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

@OptIn(ExperimentalTime::class)
fun Application.configureHealthRouting(health: HealthSystem, appMicrometerRegistry: PrometheusMeterRegistry) {
    routing {
        get("/hams/metrics") {
            // Adding prometheus: https://ktor.io/docs/micrometer-metrics.html#install_plugin
            call.respond(appMicrometerRegistry.scrape())
        }
        get("/hams/ready") {
//            call.respond(health)
            call.application.environment.log.info("Ready check")
            var now = TimeSource.Monotonic.markNow()
            val myReady = health.checkReady(now)
            call.respond(myReady)
//            call.respondText { "ready" }
        }
        get("/hams/alive") {
            call.application.environment.log.info("Alive check")
            var now = TimeSource.Monotonic.markNow()
            val myReady = health.checkAlive(now)
            call.respond(myReady)
        }
    }
}
