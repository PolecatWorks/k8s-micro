package com.polecatworks.kotlin.k8smicro.health

import com.polecatworks.kotlin.k8smicro.app.AppService
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.CompletableDeferred
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
    private val appService: AppService,
    private val metricsRegistry: PrometheusMeterRegistry,
    val health: HealthSystem,
    private val port: Int = 8079,
) {
    private val serverStarted = CompletableDeferred<Unit>()

    private var running = AtomicBoolean(false)
    private val server =
        embeddedServer(
            CIO,
            port = this.port,
            host = "0.0.0.0",
        ) {
            log.info("Health Webservice: initialising")
            install(ContentNegotiation) {
                json()
            }
            // Does not make sense to install metrics on health server unless we are concerned about its performance

            configureHealthRouting(version, appService, metricsRegistry, health, this@HealthService)

            monitor.subscribe(io.ktor.server.application.ApplicationStarted) {
                serverStarted.complete(Unit)
            }
        }

    init {
        logger.info { "Init complete" }
    }

    private suspend fun startCoroutines() =
        coroutineScope {
            // this: CoroutineScope
            running.set(true)
            logger.info("Set to run")
            launch {
                server.start(wait = true)
                running.set(false) // If we got here then definitely set running to false
            }
            val threadSleep = 100.milliseconds
            val myAlive = AliveMarginCheck("Health", threadSleep * 3) // Limit as 3x of sleep
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

    suspend fun startSuspended() = startCoroutines()

    /**
     * Wait for the health service to be fully started and listening
     */
    suspend fun waitUntilStarted() = serverStarted.await()

    /**
     * Create blocking coroutine context and wait for completion
     *
     * Dispatch web server and health service into this context
     */
    fun start() =
        runBlocking {
            logger.info { "Health coroutines: Starting" }
            startSuspended()
            logger.info { "Health coroutines: Complete" }
        }

    fun stop() {
        running.set(false)
        logger.info("Set to stop")
    }
}
