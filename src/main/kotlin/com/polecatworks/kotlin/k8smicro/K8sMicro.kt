package com.polecatworks.kotlin.k8smicro

import com.polecatworks.kotlin.k8smicro.app.AppService
import com.polecatworks.kotlin.k8smicro.health.HealthService
import com.polecatworks.kotlin.k8smicro.health.HealthSystem
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

private val logger = KotlinLogging.logger {}

/**
 * Functional definition of the microservice.
 * All the runtime code is assembled here
 */
class K8sMicro(
    val version: String,
    private val config: K8sMicroConfig
) {
    private val metricsRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    private val healthSystem = HealthSystem()
    private val healthService = HealthService(version, healthSystem, metricsRegistry, 8079)
    private val appService = AppService(healthSystem, metricsRegistry, config)
    private var running = AtomicBoolean(false)
    private val shutdownHook = thread(start = false) {
        logger.info("Starting shutdown hook")
        appService.stop()
        while (running.get()) { // Allow services time to shutdown
            Thread.sleep(100.milliseconds.inWholeMilliseconds)
        }

        logger.info("Shutdown hook complete")
    }
    init {
        Runtime.getRuntime().addShutdownHook(shutdownHook)
    }

    /**
     * start the microservice and keep the thread until it is complete and all shutdown
     */
    @OptIn(ExperimentalTime::class)
    fun run() {
        logger.info("K8sMicro starting")
        running.set(true)
        val healthThread = thread {
            healthService.start()
            appService.stop()
        }

        appService.start() // Blocks here while app is running

        healthService.stop()
        logger.info("waiting for health service thread join")
        healthThread.join()
        running.set(false)
        logger.info("K8sMicro is complete")
    }
}
