package com.polecatworks.kotlin.k8smicro

import com.polecatworks.kotlin.k8smicro.plugins.configureAppRouting
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.contentnegotiation.*
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}
class AppService(
    val health: HealthSystem,
    private val metricsRegistry: PrometheusMeterRegistry,
    private val config: K8sMicroConfig
) {

    private val running = AtomicBoolean(false)
    private val server = io.ktor.server.engine.embeddedServer(
        CIO,
        port = config.webserver.port,
        host = "0.0.0.0",
        configure = {}
    ) {
        log.info("App Webservice: initialising")
        install(ContentNegotiation) {
            json()
        }
        install(MicrometerMetrics) {
            registry = metricsRegistry
        }
        configureAppRouting(metricsRegistry)
    }

    init {
        logger.info { "App Service: Init complete" }
    }

    private suspend fun startCoroutines() = coroutineScope {
        running.set(true)
        logger.info("App Service: Set to run")
        launch {
            server.start(wait = true)
            running.set(false) // If we get here then definitely set running to false
        }
        val myAlive = HealthCheck("App coroutine", config.app.threadSleep * 3) // Limit as 3x of sleep
        health.registerAlive(myAlive)
        launch {
            while (running.get()) {
                delay(config.app.threadSleep)
                myAlive.kick()
            }
            health.deregisterAlive(myAlive)
            server.stop()
        }
    }

    /**
     * Create blocking coroutine context and wait for completion
     *
     * Dispatch web server and app service into this context
     */
    fun start() = runBlocking {
        logger.info { "App coroutines: Starting" }

        startCoroutines()

        logger.info { "App coroutines: Complete" }
    }

    fun stop() {
        running.set(false)
        logger.info("App Service: Set to stop")
    }
}
