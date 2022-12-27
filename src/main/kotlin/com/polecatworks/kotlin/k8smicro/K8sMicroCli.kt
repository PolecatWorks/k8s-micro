// This is free software, and you are welcome to redistribute it
// under certain conditions; See LICENSE file for details.

package com.polecatworks.kotlin.k8smicro

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
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
import io.micrometer.prometheus.PrometheusMeterRegistry
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

class K8sMicroCli : CliktCommand() {
    private val config by option(help = "Config file").file(canBeFile = true)
    private var running = AtomicBoolean(true)

    override fun run() {
        val version = System.getenv("VERSION") ?: "0.0.0"
        val configBuilder = ConfigLoaderBuilder.default()
        if (config == null) {
            logger.info("Loading default config from resource")
            configBuilder.addResourceSource("/k8smicro-config.yaml")
        } else {
            logger.info("Loading config from file: $config")
            configBuilder.addFileSource(config!!)
        }
        val k8sMicroConfig = configBuilder.build()
            .loadConfigOrThrow<K8sMicroConfig>()
        logger.info("Config= $k8sMicroConfig")

        val k8sMicro = K8sMicro(version, k8sMicroConfig)

        k8sMicro.run()

        logger.info("K8sMicro is exiting")
//
//        println("SLEEPING")
//        Thread.sleep(100.seconds.inWholeMilliseconds)
//
//
//        // Register our safe shutdown procedure
//        val shutdownHook = thread(start = false) {
//            logger.info("Starting the shutdown process. Will take a little while")
//
//            Thread.sleep(1000)
//            logger.info { "Set running to false to shut us down" }
//            running.set(false)
//            Thread.sleep(1000)
//            logger.info("Shutdown prep complete. Now going to close")
//        }
//        Runtime.getRuntime().addShutdownHook(shutdownHook)
//
//        val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
//
//        // Construct our health system
//        val myHealthSystem = HealthSystem()
// //        val myHealthService = HealthService(version,myHealthSystem,metricsRegistry)
// //        val healthThread = thread {
// //            myHealthService.start()
// ////            healthWebServer(myHealthSystem, running, appMicrometerRegistry, version)
// //        }
//
//        val appThread = thread {
//            appWebServer(myHealthSystem, running, k8sMicroConfig.webserver, appMicrometerRegistry)
//        }
//
//        // Start a randome side thread that ..... may be wobbly so might fail on us after 5 secs
//        logger.info { "Starting the thread" }
//        val randomThread = thread {
//            val myh = HealthCheck("randomThread", 30.seconds)
//            myHealthSystem.registerAlive(myh)
//            println("Started in my random thread")
//            for (i in 0..100) {
//                Thread.sleep(k8sMicroConfig.randomThread.sleepTime.inWholeMilliseconds)
//                myh.kick()
//            }
//            myHealthSystem.deregisterAlive(myh)
//            println("Random thread is now done")
//        }
//
//        randomThread.join()
//
//        logger.info { "Something happened our threads so we shutdown safely by setting running=false" }
//        running.set(false)
//
// //        healthThread.join()
//        appThread.join()
//        shutdownHook.join()
//
//        logger.info("Successfully closed")
    }
}

fun main(args: Array<String>) = K8sMicroCli().main(args)

fun appWebServer(health: HealthSystem, running: AtomicBoolean, config: WebServer, appMicrometerRegistry: PrometheusMeterRegistry) {
    logger.info { "Starting health server" }
    val healthApiServer = embeddedServer(
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
//        configureAppRouting(metricsRegistry)
    }
        .start(wait = false)

    logger.info("Running app webserver until stopped")

//    val ben = runBlocking { // this: CoroutineScope
//        launch { // launch a new coroutine and continue
//            delay(1000L) // non-blocking delay for 1 second (default time unit is ms)
//            println("World!") // print after delay
//        }
//        println("Hello") // main coroutine continues while a previous one is delayed
//    }

    while (running.get()) {
        Thread.sleep(1000)

        logger.info("App webserver is alive")
    }
    logger.info("App Webserver is done")

    healthApiServer.stop(100L, 1000L)
    logger.info("Health stopped")
}

fun healthWebServer(health: HealthSystem, running: AtomicBoolean, appMicrometerRegistry: PrometheusMeterRegistry, version: String) {
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
        configureHealthRouting(health, appMicrometerRegistry, version)
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
