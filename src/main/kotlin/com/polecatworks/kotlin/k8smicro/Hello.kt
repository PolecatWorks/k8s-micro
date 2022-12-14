package com.polecatworks.kotlin.k8smicro

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import com.polecatworks.kotlin.k8smicro.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.*

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}



class Hello : CliktCommand() {
    val count: Int by option(help = "Number of greetings").int().default(1)
    val name: String by option(help = "The person to greet").prompt("Your name")

    override fun run() {
        repeat(count) {
            echo("Hello $name!")
        }
        logger.info { "Starting the thread" }
        startServer(arrayOf())
    }
}

fun main(args: Array<String>) = Hello().main(args)

fun startServer(args: Array<String>) {


//    logger.info{"Starting server"}
    embeddedServer(CIO, port = 8079, host = "0.0.0.0",
        configure = {
            connectionIdleTimeoutSeconds = 45
        }) {
        log.info("Hello from module!")
        install(ContentNegotiation) {
            json()
        }
        configureRouting()
    }
        .start(wait = true)

}
//
//fun Application.module() {
//    log.info("Hello from module!")
//    install(ContentNegotiation) {
//        json()
//    }
//    configureRouting()
////    install()
//}