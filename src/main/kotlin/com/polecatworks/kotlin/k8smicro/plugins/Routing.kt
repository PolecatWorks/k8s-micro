package com.polecatworks.kotlin.k8smicro.plugins

import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*

import kotlinx.serialization.Serializable

@Serializable
data class Customer(val id: Int, val firstName: String, val lastName: String)

fun Application.configureRouting() {
    routing {
        get("/") {
//            log.info("serving hello")
            call.application.environment.log.info("Hello from /api/v1!")
            call.respondText("Hello World!")
        }
        get("/health/ready") {
            call.respondText { "ready" }
        }
        get("/health/alive") {

            val customer = Customer(1,"Ben", "Greene")
            call.respond(customer)

//            call.respondText { "Alive" }
        }
    }
}