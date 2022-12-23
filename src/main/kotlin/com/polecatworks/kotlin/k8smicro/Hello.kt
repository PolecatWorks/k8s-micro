// This is free software, and you are welcome to redistribute it
// under certain conditions; See LICENSE file for details.

package com.polecatworks.kotlin.k8smicro

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.polecatworks.kotlin.k8smicro.plugins.configureAppRouting
import com.polecatworks.kotlin.k8smicro.plugins.configureHealthRouting
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addFileSource
import com.sksamuel.hoplite.addResourceSource
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.contentnegotiation.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

class Hello : CliktCommand() {
    private val config by option(help = "Config file").file(canBeFile = true)
    private var running = AtomicBoolean(true)

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

        // Register our safe shutdown procedure
        val shutdownHook = thread(start = false) {
            logger.info("Starting the shutdown process. Will take a little while")

            Thread.sleep(1000)
            logger.info { "Set running to false to shut us down" }
            running.set(false)
            Thread.sleep(1000)
            logger.info("Shutdown prep complete. Now going to close")
        }
        Runtime.getRuntime().addShutdownHook(shutdownHook)

        val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

        // Construct our health system
        val myHealth = HealthSystem()
        val healthThread = thread {
            healthWebServer(myHealth, running, appMicrometerRegistry)
        }

        val appThread = thread {
            appWebServer(myHealth, running, config.webserver, appMicrometerRegistry)
        }

        // Start a randome side thread that ..... may be wobbly so might fail on us after 5 secs
        logger.info { "Starting the thread" }
        val randomThread = thread {
            val myh = HealthCheck("randomThread", 30.seconds)
            myHealth.registerAlive(myh)
            println("Started in my random thread")
            for (i in 0..100) {
                Thread.sleep(config.randomThread.sleepTime.inWholeMilliseconds)
                myh.kick()
            }
            myHealth.deregisterAlive(myh)
            println("Random thread is now done")
        }

        randomThread.join()

        logger.info { "Something happened our threads so we shutdown safely by setting running=false" }
        running.set(false)

        healthThread.join()
        appThread.join()
        shutdownHook.join()

        logger.info("Successfully closed")
    }
}

fun main(args: Array<String>) = Hello().main(args)

fun appWebServer(health: HealthSystem, running: AtomicBoolean, config: WebServer, appMicrometerRegistry: PrometheusMeterRegistry) {
    logger.info { "Starting health server" }
    val myserver = embeddedServer(
        CIO,
        port = config.port,
        host = "0.0.0.0",
        configure = {
            connectionIdleTimeoutSeconds = 45
        }
    ) {
        log.info("Hello from module!")
        install(ContentNegotiation) {
            json()
        }
        install(MicrometerMetrics) {
            registry = appMicrometerRegistry
        }
        configureAppRouting()
    }
        .start(wait = false)

    logger.info("Running app webserver until stopped")
    while (running.get()) {
        Thread.sleep(1000)
        logger.info("App webserver is alive")
    }
    logger.info("App Webserver is done")

    myserver.stop(100L, 1000L)
    logger.info("Health stopped")
}

fun healthWebServer(health: HealthSystem, running: AtomicBoolean, appMicrometerRegistry: PrometheusMeterRegistry) {
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
        // Does not make sense to install metrics on health server unless we are concerned about its performance
        configureHealthRouting(health, appMicrometerRegistry)
    }
        .start(wait = false)

    logger.info("Running until stopped")
    while (running.get()) {
        Thread.sleep(1000)
        logger.info("Health system is alive")
    }
    logger.info("Alive is done")

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
