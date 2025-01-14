package com.polecatworks.kotlin.k8smicro.app

import com.papsign.ktor.openapigen.annotations.Path
import com.papsign.ktor.openapigen.annotations.Response
import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.annotations.parameters.QueryParam
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import mu.KotlinLogging

// Path works like the @Location from locations, but for transparency we recommend only using it to extract the parameters
@Path("string/{a}")
data class StringParam(
    @PathParam("A simple String Param") val a: String,
    @QueryParam("Optional String") val optional: String? // Nullable Types are optional
)

// A response can be any class, but a description will be generated from the annotation
@Response("A String Response")
@Serializable
data class StringResponse(val str: String)

@Response("Count of current number of active counts")
@Serializable
data class CountResponse(val count: Int)

private val log = KotlinLogging.logger {}

fun Application.configureAppRouting(appService: AppService) {
    apiRouting {
        // Sample OpenAPI3 annotations: https://github.com/papsign/Ktor-OpenAPI-Generator/wiki/A-few-examples
        get<StringParam, String> { params ->
            respond("Smoke" + params.a)
        }
        route(appService.config.webserver.prefix) {
            get<Unit, StringResponse> { _ ->
                log.info("Hello from /api/v1")
                respond(StringResponse("params.a"))
            }
            route("/").get<Unit, StringResponse> { _params ->
                log.info("Hello from /api/v1/")
                respond(StringResponse("params.a"))
            }
            route("count").get<Unit, CountResponse> { _ ->
                val tempCount = appService.state.count.incrementAndGet()
                log.info("Counting up, currently = $tempCount")
                respond(CountResponse(tempCount))
            }
        }
    }
}
