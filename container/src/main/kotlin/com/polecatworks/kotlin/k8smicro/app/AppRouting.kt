package com.polecatworks.kotlin.k8smicro.app

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import mu.KotlinLogging

// // Path works like the @Location from locations, but for transparency we recommend only using it to extract the parameters
// @Path("string/{a}")
// data class StringParam(
//    @PathParam("A simple String Param") val a: String,
//    @QueryParam("Optional String") val optional: String? // Nullable Types are optional
// )
//
// // A response can be any class, but a description will be generated from the annotation
// @Response("A String Response")
// @Serializable
// data class StringResponse(val str: String)
//
// @Response("Count of current number of active counts")
// @Serializable
// data class CountResponse(val count: Int)

private val log = KotlinLogging.logger {}

fun Application.configureAppRouting(appService: AppService) {
    routing {
        route(appService.myWebserver.prefix) {
            route("/hello", HttpMethod.Get) {
                handle {
                    call.respondText("Hello back")
                }
            }

            get("/chaser/{key}") {
                val key = call.parameters["key"]
                if (key.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "Missing key")
                    return@get
                }
                val value = appService.getAggregate(key)
                if (value == null) {
                    call.respond(HttpStatusCode.NotFound, "Not found")
                } else {
                    call.respond(value)
                }
            }

            get("/chaser") {
                val keys = appService.getAllChaserAggregateKeys()
                call.respond(keys)
            }

            get("/billing/{key}") {
                val key = call.parameters["key"]
                if (key.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "Missing key")
                    return@get
                }
                val value = appService.getBillingAggregate(key)
                if (value == null) {
                    call.respond(HttpStatusCode.NotFound, "Not found")
                } else {
                    call.respond(value)
                }
            }

            get("/billing") {
                val keys = appService.getAllBillingAggregateKeys()
                call.respond(keys)
            }
        }
    }
}
