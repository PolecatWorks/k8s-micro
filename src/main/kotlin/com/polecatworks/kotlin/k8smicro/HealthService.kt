package com.polecatworks.kotlin.k8smicro

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}
class HealthService(val port: Int = 8079) {
    private var running = AtomicBoolean(false)
    private val server = io.ktor.server.engine.embeddedServer(
        CIO,
        port = this.port,
        host = "0.0.0.0",
        configure = {
        }
    ) {
        log.info("Hello from HealthService")
        install(ContentNegotiation) {
            json()
        }
        // Does not make sense to install metrics on health server unless we are concerned about its performance
        // configureHealthRouting(health, appMicrometerRegistry)
    }

    init {
        logger.info { "Starting HealthService" }
        println("starting")
    }

    private suspend fun startCoroutines() = coroutineScope { // this: CoroutineScope
        running.set(true)
        launch {
            startWebServer()
            println("Go me here")
        }
        launch {
            while (running.get()) {
                delay(500.milliseconds.inWholeMilliseconds)
                println("should kick here")
//                Add health check kick here
            }
            println("HealthService thread is complete so stopping web service")
            server.stop(1.seconds.inWholeMilliseconds, 100.milliseconds.inWholeMilliseconds)
            println("HealthService web service stopped")
        }
    }

    private suspend fun startWebServer() = coroutineScope {
        server.start(wait = true)
        println("Web service is done")
    }

    fun start() = runBlocking {
        startCoroutines()
        println("Done with coroutines")
    }

    fun stop() {
        running.set(false)
    }
}
