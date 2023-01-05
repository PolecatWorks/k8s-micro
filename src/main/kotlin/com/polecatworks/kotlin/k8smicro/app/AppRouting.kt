package com.polecatworks.kotlin.k8smicro.app

import com.papsign.ktor.openapigen.annotations.Path
import com.papsign.ktor.openapigen.annotations.Response
import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.annotations.parameters.QueryParam
import com.papsign.ktor.openapigen.openAPIGen
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.response.respond
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kserialize
// Path works like the @Location from locations, but for transparency we recommend only using it to extract the parameters
@Path("string/{a}")
data class StringParam(
    @PathParam("A simple String Param") val a: String,
    @QueryParam("Optional String") val optional: String? // Nullable Types are optional
)

// A response can be any class, but a description will be generated from the annotation
@Response("A String Response")
data class StringResponse(val str: String)

fun Application.configureAppRouting(appService: AppService) {
    apiRouting {
        get<StringParam, StringResponse> { params ->
            respond(StringResponse(params.a))
        }

        routing {
            route(appService.config.webserver.prefix) {
                get("/") {
                    call.application.environment.log.info("Hello from /api/v1!")
                    call.respondText("Hello World!")
                }
                get("/count") {
                    val tempCount = appService.state.count.incrementAndGet()
                    call.respondText("Count = $tempCount")
                }
                get("/openapi") {
                    call.respond(application.openAPIGen.api.kserialize())
                }
            }
        }
    }
}
