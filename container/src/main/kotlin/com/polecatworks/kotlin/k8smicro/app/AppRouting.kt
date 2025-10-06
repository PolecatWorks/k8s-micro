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
        route("/hello", HttpMethod.Get) {
            handle {
                call.respondText("Hello back")
            }
        }

        get("/store/{key}") {
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

        get("/store") {
            val keys = appService.getAllAggregateKeys()
            call.respond(keys)
        }
    }
//    apiRouting {
//        // Sample OpenAPI3 annotations: https://github.com/papsign/Ktor-OpenAPI-Generator/wiki/A-few-examples
//        get<StringParam, String> { params ->
//            respond("Smoke" + params.a)
//        }
//        route(appService.config.webserver.prefix) {
//            get<Unit, StringResponse> { _ ->
//                log.info("Hello from /api/v1")
//                respond(StringResponse("params.a"))
//            }
//            route("/").get<Unit, StringResponse> { _params ->
//                log.info("Hello from /api/v1/")
//                respond(StringResponse("params.a"))
//            }
//            route("count").get<Unit, CountResponse> { _ ->
//                val tempCount = appService.state.count.incrementAndGet()
//                log.info("Counting up, currently = $tempCount")
//                respond(CountResponse(tempCount))
//            }
//        }
//    }
}
