package com.polecatworks.kotlin.k8smicro.app

import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.openAPIGen
import com.polecatworks.kotlin.k8smicro.K8sMicroConfig
import com.polecatworks.kotlin.k8smicro.health.AliveMarginCheck
import com.polecatworks.kotlin.k8smicro.health.HealthSystem
import com.polecatworks.kotlin.k8smicro.health.ReadyStateCheck
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
import java.util.concurrent.atomic.AtomicInteger

private val logger = KotlinLogging.logger {}

data class AppServiceState(
    var count: AtomicInteger
)

class AppService(
    private val health: HealthSystem,
    private val metricsRegistry: PrometheusMeterRegistry,
    val config: K8sMicroConfig
) {

    private val running = AtomicBoolean(false)
    private val server = io.ktor.server.engine.embeddedServer(
        CIO,
        port = config.webserver.port,
        host = "0.0.0.0",
        configure = {}
    ) {
        log.info("App Webservice: initialising")
        install(OpenAPIGen) {
            // serveOpenApiJson = true
            info {
                title = "k8s-minimal"
                version = "0.0.0"
                description = "K8s Micro API"
                contact {
                    name = "Ben Greene"
                    email = "BenJGreene+polecatworks@gmail.com"
                }
            }
        }
        install(ContentNegotiation) {
            json()
        }
        install(MicrometerMetrics) {
            registry = metricsRegistry
        }
        configureAppRouting(this@AppService)
    }
    val openAPIGen get() = this.server.application.openAPIGen
    val state = AppServiceState(
        AtomicInteger(0)
    )
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
        val myAlive = AliveMarginCheck("App coroutine", config.app.threadSleep * 3) // Limit as 3x of sleep
        val myReady = ReadyStateCheck("App coroutine")
        health.registerAlive(myAlive)
        health.registerReady(myReady)
        launch {
            while (running.get()) {
                delay(config.app.threadSleep)
                val myCount = state.count.get()
                if (myCount > 5) {
                    if (myReady.busy()) {
                        logger.info("Setting BUSY")
                    }
                } else if (myCount == 0) {
                    if (myReady.ready()) {
                        logger.info("Setting READY")
                    }
                }
                if (myCount > 0) {
                    state.count.decrementAndGet()
                }
                myAlive.kick()
            }
            health.deregisterAlive(myAlive)
            health.deregisterReady(myReady)
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
