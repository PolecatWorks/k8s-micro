package com.polecatworks.kotlin.k8smicro

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.mockk.mockk
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class K8sMicroTest {

    @Test
    fun testHello() {
        println("I am a useless test")
    }

    @Test
    fun testCoroutine() {
        runBlocking { // this: CoroutineScope
            coroutineScope {
                launch { // launch a new coroutine and continue
                    delay(1.seconds) // non-blocking delay for 1 second (default time unit is ms)
                    println("World!") // print after delay
                }
                launch { // launch a new coroutine and continue
                    delay(500.milliseconds) // non-blocking delay for 1 second (default time unit is ms)
                    println("World ish!") // print after delay
                }
            }
            println("Hello") // main coroutine continues while a previous one is delayed
        }
    }

    @Test
    fun healthService(): Unit = runBlocking {
        // Test startup and shutdown
        // Test simple http working for version endpoint
        val mockMetricsRegistry: PrometheusMeterRegistry = mockk()
        val mockHealthSystem: HealthSystem = mockk()

        val version = "v1.0.0"

        val healthService = HealthService(
            version,
            mockHealthSystem,
            mockMetricsRegistry
        )
        val healthThread = thread {
            healthService.start()
        }

        val response = HttpClient(CIO).get("http://localhost:${healthService.port}/hams/version")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(version, response.bodyAsText())

        healthService.stop()
        healthThread.join()
        println("Done with test")
    }
}
