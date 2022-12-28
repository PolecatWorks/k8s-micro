package com.polecatworks.kotlin.k8smicro

import com.polecatworks.kotlin.k8smicro.plugins.configureHealthRouting
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.plugins.contentnegotiation.*
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}
class HealthService(
    val version: String,
    val health: HealthSystem,
    val metricsRegistry: PrometheusMeterRegistry,
    val port: Int = 8079
) {
    private var running = AtomicBoolean(false)
    private val server = io.ktor.server.engine.embeddedServer(
        CIO,
        port = this.port,
        host = "0.0.0.0",
        configure = {
        }
    ) {
        log.info("Health Webservice: initialising")
        install(ContentNegotiation) {
            json()
        }
        // Does not make sense to install metrics on health server unless we are concerned about its performance

        configureHealthRouting(health, metricsRegistry, version)
    }

    init {
        logger.info { "Init complete" }
    }

    private suspend fun startCoroutines() = coroutineScope { // this: CoroutineScope
        running.set(true)
        logger.info("Set to run")
        launch {
            server.start(wait = true)
            running.set(false) // If we got here then definitely set running to false
        }
        val threadSleep = 100.milliseconds
        val myAlive = HealthCheck("Health coroutine", threadSleep * 3) // Limit as 3x of sleep
        health.registerAlive(myAlive)
        launch {
            while (running.get()) {
                delay(threadSleep)
                myAlive.kick()
            }
            health.deregisterAlive(myAlive)
            server.stop(1.seconds.inWholeMilliseconds, 100.milliseconds.inWholeMilliseconds)
        }
    }

    /**
     * Create blocking coroutine context and wait for completion
     *
     * Dispatch web server and health service into this context
     */
    fun start() = runBlocking {
        logger.info { "Health coroutines: Starting" }
        startCoroutines()
        logger.info { "Health coroutines: Complete" }
    }

    fun stop() {
        running.set(false)
        logger.info("Set to stop")
    }
}
