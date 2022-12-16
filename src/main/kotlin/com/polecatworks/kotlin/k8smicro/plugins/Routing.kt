package com.polecatworks.kotlin.k8smicro.plugins

import com.polecatworks.kotlin.k8smicro.HealthSystem
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class Customer(val id: Int, val firstName: String, val lastName: String)

fun Application.configureRouting(health: HealthSystem) {
    routing {
        get("/") {
            call.application.environment.log.info("Hello from /api/v1!")
            call.respondText("Hello World!")
        }
        get("/health/ready") {
//            call.respond(health)
            call.application.environment.log.info("Ready check")
            call.respondText { "ready" }
        }
        get("/health/alive") {
            call.application.environment.log.info("Alive check")
            val customer = Customer(1, "Ben", "Greene")
            call.respond(customer)
        }
    }
}
