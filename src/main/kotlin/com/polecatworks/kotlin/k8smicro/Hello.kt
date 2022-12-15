package com.polecatworks.kotlin.k8smicro

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.polecatworks.kotlin.k8smicro.models.Alive
import com.polecatworks.kotlin.k8smicro.plugins.configureRouting
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addFileSource
import com.sksamuel.hoplite.addResourceSource
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import mu.KotlinLogging
import java.net.URL
import java.nio.file.Paths


private val logger = KotlinLogging.logger {}


class Hello : CliktCommand() {
//    val count: Int by option(help = "Number of greetings").int().default(1)
    val config: String by option(help = "Config file").default(Hello::class.java.getResource("k8smicro-config.yaml").file)

    override fun run() {
        println(config)
        logger.info("Loading config from: ${config}")
        val config = ConfigLoaderBuilder.default()
            .addFileSource(config)
            .build()
            .loadConfigOrThrow<Config>()
        println(config)

        val myHealth = arrayOf(Alive("hello1", true), Alive("hello2", false))

        logger.info { "Starting the thread" }
        startServer(myHealth)
    }
}

fun main(args: Array<String>) = Hello().main(args)

fun startServer(health: Array<Alive>) {



    logger.info{"Starting server"}
    embeddedServer(CIO, port = 8079, host = "0.0.0.0",
        configure = {
            connectionIdleTimeoutSeconds = 45
        }) {
        log.info("Hello from module!")
        install(ContentNegotiation) {
            json()
        }
        configureRouting(health)
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