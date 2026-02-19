package com.polecatworks.kotlin.k8smicro

import com.polecatworks.kotlin.k8smicro.app.AppService
import com.polecatworks.kotlin.k8smicro.health.HealthService
import com.polecatworks.kotlin.k8smicro.health.HealthSystem
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import mu.KotlinLogging
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

private val logger = KotlinLogging.logger {}

/**
 * Functional definition of the microservice.
 * All the runtime code is assembled here.
 *
 * This class orchestrates the lifecycle of the application, including the health service,
 * app service, and shutdown hooks.
 *
 * @property version The version of the microservice.
 * @property config The configuration for the microservice.
 */
class K8sMicro(
    val version: String,
    private val config: K8sMicroConfig,
) {
    private val metricsRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    private val healthSystem = HealthSystem()
    private val appService = AppService(healthSystem, metricsRegistry, config)
    private val healthService = HealthService(version, appService, metricsRegistry, healthSystem, 8079)
    private val finishedLatch = CountDownLatch(1)
    private val shutdownHook =
        thread(start = false) {
            logger.info("Starting shutdown hook")
            appService.stop()
            finishedLatch.await() // Wait for system to finish cleanup
            logger.info("Shutdown hook complete")
        }

    init {
        Runtime.getRuntime().addShutdownHook(shutdownHook)
    }

    /**
     * Start the microservice and keep the thread running until it is complete and all components have shutdown.
     *
     * This method starts the health service in a separate thread and then starts the main app service.
     * It blocks until the app service stops (e.g., via shutdown hook).
     */
    fun run() {
        logger.info("K8sMicro starting")

        val healthThread =
            thread {
                healthService.start()
                appService.stop()
            }

        appService.start() // Blocks here while app is running

        healthService.stop()
        logger.info("waiting for health service thread join")
        healthThread.join()
        finishedLatch.countDown()
        logger.info("K8sMicro is complete")
    }
}
