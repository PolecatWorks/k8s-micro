package com.polecatworks.kotlin.k8smicro

import com.polecatworks.kotlin.k8smicro.health.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.testing.*
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource.Monotonic.markNow

class HealthTest {

    @Test
    @OptIn(ExperimentalTime::class)
    fun testHealthCheck() {
        // Check valid until margin is met
        // Check invalid after margin
        val marginTime = 1.seconds

        val beforeConstruct = markNow()
        val dut = AliveMarginCheck("dut", marginTime)
        val afterConstruct = markNow()

        assert(dut.latest > beforeConstruct)
        assert(afterConstruct > dut.latest)

        val atMarginEnd = dut.latest + marginTime

        val checkAtMarginEnd = dut.check(atMarginEnd)
        assert(checkAtMarginEnd.valid)
        val afterMarginEnd = dut.latest + marginTime + 1.seconds
        val checkAfterMarginEnd = dut.check(afterMarginEnd)

        assertNotEquals(checkAtMarginEnd, checkAfterMarginEnd)
        assertFalse(checkAfterMarginEnd.valid)

        // Check kick updates latest
        val oldLatest = dut.latest
        dut.kick()
        val newLatest = dut.latest
        assert(newLatest > oldLatest)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun testHealthSystem() = runBlocking {
        // Check register, deRegister
        // Check times both in margin, 1 in margin and after removing entry
        // Check no items in health list
        val hs = HealthSystem()

        val marginTimeShort = 500.milliseconds
        val marginTimeLong = 1.seconds

        val myHealthShort = AliveMarginCheck("short", marginTimeShort)
        val myHealthLong = AliveMarginCheck("long", marginTimeLong)

        hs.registerAlive(myHealthShort)
        hs.registerAlive(myHealthLong)

        val atShortMarginEnd = myHealthShort.latest + marginTimeShort
        val checkAtShortMarginEnd = hs.checkAlive(atShortMarginEnd)
        assert(checkAtShortMarginEnd.valid)

        val afterShortMarginEnd = myHealthShort.latest + marginTimeShort + 1.milliseconds
        val checkAfterShortMarginEnd = hs.checkAlive(afterShortMarginEnd)
        assertFalse(checkAfterShortMarginEnd.valid)

        hs.deregisterAlive(myHealthShort)
        val checkAfterShortMarginEndShortRemoved = hs.checkAlive(afterShortMarginEnd)
        assert(checkAfterShortMarginEndShortRemoved.valid)

        val afterLongMarginEnd = myHealthLong.latest + marginTimeLong + 1.milliseconds
        val checkAfterLongMarginEnd = hs.checkAlive(afterLongMarginEnd)
        assertFalse(checkAfterLongMarginEnd.valid)

        hs.deregisterAlive(myHealthLong)
        val checkAfterLongMarginEndRemoved = hs.checkAlive(afterLongMarginEnd)
        assert(checkAfterLongMarginEndRemoved.valid)
    }

    @Test
    fun mockkPrometheusMeterRegistry() {
        val mockPrometheusMeterRegistry = mockk<PrometheusMeterRegistry>()
        every { mockPrometheusMeterRegistry.scrape() } returns "My Metrics"
        assertEquals("My Metrics", mockPrometheusMeterRegistry.scrape())
        verify {
            mockPrometheusMeterRegistry.scrape()
        }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun mockHealthSystem() {
        val mockHealthSystem: HealthSystem = mockk()
        every { mockHealthSystem.checkAlive(any()) } returns HealthSystemResult("alive", true, listOf())
        val myResult = mockHealthSystem.checkAlive(markNow())
        verify {
            mockHealthSystem.checkAlive(any())
        }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun embeddedHealthApi() = testApplication {
        val mockPrometheusMeterRegistry: PrometheusMeterRegistry = mockk()
        every { mockPrometheusMeterRegistry.scrape() } returns "My Metrics"

        val mockHealthSystem: HealthSystem = mockk()
        every { mockHealthSystem.checkAlive(any()) } returns HealthSystemResult("alive", true, listOf())
        every { mockHealthSystem.checkReady(any()) } returns HealthSystemResult("ready", true, listOf())

        application {
            install(ContentNegotiation) {
                json()
            }
            configureHealthRouting(
                mockHealthSystem,
                mockPrometheusMeterRegistry,
                "v1.0.0"
            )
        }
        val versionResponse = client.get("/hams/version")
        assertEquals(HttpStatusCode.OK, versionResponse.status)
        assertEquals("v1.0.0", versionResponse.bodyAsText())

        val metricsResponse = client.get("/hams/metrics")
        assertEquals(HttpStatusCode.OK, metricsResponse.status)
        verify {
            mockPrometheusMeterRegistry.scrape()
        }
        assertEquals("My Metrics", metricsResponse.bodyAsText())

        val aliveResponse = client.get("/hams/alive")
        assertEquals(HttpStatusCode.OK, aliveResponse.status)
        verify {
            mockHealthSystem.checkAlive(any())
        }

        val readyResponse = client.get("/hams/ready")
        assertEquals(HttpStatusCode.OK, readyResponse.status)
        verify {
            mockHealthSystem.checkReady(any())
        }
    }

    @Test
    fun healthService(): Unit = runBlocking {
        // Test startup and shutdown
        // Test simple http working for version endpoint
        val mockMetricsRegistry: PrometheusMeterRegistry = mockk()
        val mockHealthSystem: HealthSystem = mockk()

        coEvery { mockHealthSystem.registerAlive(any()) } returns true
        coEvery { mockHealthSystem.deregisterAlive(any()) } returns true

        val version = "v1.0.0"

        val healthService = HealthService(
            version,
            mockHealthSystem,
            mockMetricsRegistry
        )
        val healthThread = thread {
            println("starting")
            healthService.start()
            println("done")
        }
        val healthPort = 8079

        val response = HttpClient(CIO).get("http://localhost:$healthPort/hams/version")

        Assert.assertEquals(HttpStatusCode.OK, response.status)
        Assert.assertEquals(version, response.bodyAsText())

        healthService.stop()
        healthThread.join()
        println("Done with test")
    }
}
