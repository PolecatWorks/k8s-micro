package com.polecatworks.kotlin.k8smicro

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
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
import kotlin.concurrent.thread

private val logger = KotlinLogging.logger {}

class Hello : CliktCommand() {
    private val config by option(help = "Config file").file(canBeFile = true)

    override fun run() {
        val configBuilder = ConfigLoaderBuilder.default()
        if (config == null) {
            logger.info("Loading default config from resource")
            configBuilder.addResourceSource("/k8smicro-config.yaml")
        } else {
            logger.info("Loading config from file: $config")
            configBuilder.addFileSource(config!!)
        }
        val config = configBuilder.build()
            .loadConfigOrThrow<Config>()
        logger.info("Config= $config")

        val myHealth = arrayOf(Alive("hello1", true), Alive("hello2", false))

        logger.info { "Starting the thread" }
        val ben = thread {
            println("i am thread")
            Thread.sleep(5000)
            println("Done me")
        }

        val printingHook = thread(start = false) {
            println("Starting the shutdown process. Will take a little while")
            Thread.sleep(10000)
            println("Now going to close")
        }
        Runtime.getRuntime().addShutdownHook(printingHook)

        healthWebServer(myHealth)

        ben.join()
        printingHook.join()

        logger.info("Successfully closed")
    }
}

fun main(args: Array<String>) = Hello().main(args)

fun healthWebServer(health: Array<Alive>) {
    logger.info { "Starting health server" }
    val myserver = embeddedServer(
        CIO,
        port = 8079,
        host = "0.0.0.0",
        configure = {
            connectionIdleTimeoutSeconds = 45
        }
    ) {
        log.info("Hello from module!")
        install(ContentNegotiation) {
            json()
        }
        configureRouting(health)
    }
        .start(wait = false)

    logger.info("Sleeping for a bit")
    Thread.sleep(10000)
    logger.info("Slept enough")

    myserver.stop(100L, 1000L)
    logger.info("Health stopped")
}
//
// fun Application.module() {
//    log.info("Hello from module!")
//    install(ContentNegotiation) {
//        json()
//    }
//    configureRouting()
// //    install()
// }
