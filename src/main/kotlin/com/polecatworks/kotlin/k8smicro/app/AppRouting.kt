package com.polecatworks.kotlin.k8smicro.app

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureAppRouting(application: AppService) {
    routing {
        get("/k8s-micro/v0/") {
            call.application.environment.log.info("Hello from /api/v1!")
            call.respondText("Hello World!")
        }
        get("/k8s-micro/v0/count") {
            val tempCount = application.state.count.incrementAndGet()
            call.respondText("Count = $tempCount")
        }
    }
}
