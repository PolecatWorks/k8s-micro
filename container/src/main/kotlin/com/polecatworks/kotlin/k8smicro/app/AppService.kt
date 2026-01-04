package com.polecatworks.kotlin.k8smicro.app

import com.polecatworks.kotlin.k8smicro.K8sMicroConfig
import com.polecatworks.kotlin.k8smicro.KafkaProcessor
import com.polecatworks.kotlin.k8smicro.SqlServer
import com.polecatworks.kotlin.k8smicro.eventSerde.Event
import com.polecatworks.kotlin.k8smicro.health.AliveMarginCheck
import com.polecatworks.kotlin.k8smicro.health.HealthSystem
import com.polecatworks.kotlin.k8smicro.health.ReadyStateCheck
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.calllogging.CallLogging
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import io.ktor.client.engine.cio.CIO as CIO_CLIENT
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.cio.CIO as CIO_SERVER
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

val logger = KotlinLogging.logger {}

data class AppServiceState(
    var count: AtomicInteger,
)

class AppService(
    private val health: HealthSystem,
    private val metricsRegistry: PrometheusMeterRegistry,
    val config: K8sMicroConfig,
) {
    private val running = AtomicBoolean(false)
    private val server =
        embeddedServer(
            CIO_SERVER,
            port = config.webserver.port,
            host = "0.0.0.0",
//        configure = {}
        ) {
            log.info("App Webservice: initialising")
            install(CallLogging) {
                // level = Level.INFO
            }
            install(ServerContentNegotiation) {
                json()
            }
            install(MicrometerMetrics) {
                registry = metricsRegistry
            }
            configureAppRouting(this@AppService)
        }
    val state =
        AppServiceState(
            AtomicInteger(0),
        )

    init {
        logger.info { "App Service: Init complete" }
    }

    private val myHost = InetAddress.getLocalHost().hostName
    private val myPort = config.webserver.port
    val myWebserver = config.webserver
    private val applicationServer = "$myHost:$myPort"

    private val httpClient =
        HttpClient(CIO_CLIENT.create()) {
            install(ClientContentNegotiation) {
                json()
            }
        }

    private val kafkaProcessor = KafkaProcessor(config.kafkaProcessor, health, CIO_CLIENT.create(), running, applicationServer)
    private val sqlServer = SqlServer(config.sqlServer, health, running)

    private suspend fun startCoroutines() =
        coroutineScope {
            running.set(true)
            logger.info("App Service: Set to run")
            launch {
                server.start(wait = true)
                running.set(false) // If we get here then definitely set running to false
            }

            launch { kafkaProcessor.start() }
//        launch { sqlServer.start() }

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
    fun start() =
        runBlocking {
            logger.info { "App coroutines: Starting" }

            startCoroutines()

            logger.info { "App coroutines: Complete" }
        }

    fun stop() {
        running.set(false)
        logger.info("App Service: Set to stop")
    }

    suspend fun getAggregate(key: String): Event? {
        val metadata = kafkaProcessor.getStoreMetaData(key)
        if (metadata != null) {
            val activeHost = metadata.activeHost()
            if (activeHost.host() == myHost && activeHost.port() == myPort) {
                return kafkaProcessor.getAggregate(key)
            } else {
                logger.info { "Forwarding request for $key to ${activeHost.host()}:${activeHost.port()}" }
                return try {
                    httpClient.get("http://${activeHost.host()}:${activeHost.port()}/store/$key").body()
                } catch (e: Exception) {
                    logger.error(e) { "Forwarding failed to $activeHost" }
                    null
                }
            }
        }
        return kafkaProcessor.getAggregate(key)
    }

    fun getAllAggregateKeys(): List<String> = kafkaProcessor.getAllAggregateKeys()
}
