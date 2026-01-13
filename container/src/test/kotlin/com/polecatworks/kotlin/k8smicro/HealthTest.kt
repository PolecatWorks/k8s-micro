package com.polecatworks.kotlin.k8smicro

import com.polecatworks.kotlin.k8smicro.app.AppService
import com.polecatworks.kotlin.k8smicro.health.AliveMarginCheck
import com.polecatworks.kotlin.k8smicro.health.HealthService
import com.polecatworks.kotlin.k8smicro.health.HealthSystem
import com.polecatworks.kotlin.k8smicro.health.configureHealthRouting
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
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
    fun testHealthSystem() =
        runBlocking {
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
        val mockHealthSystem: HealthSystem = spyk(HealthSystem())
        val myResult = mockHealthSystem.checkAlive(markNow())
        assert(myResult.valid)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun embeddedHealthApi() =
        testApplication {
            val appService: AppService = mockk(relaxed = true)

            val mockPrometheusMeterRegistry: PrometheusMeterRegistry = mockk(relaxed = true)
            every { mockPrometheusMeterRegistry.scrape() } returns "My Metrics"

            val mockHealthSystem: HealthSystem = spyk(HealthSystem())
            // No need to mock checkAlive/checkReady as they return valid results by default

            val mockHealthService: HealthService = mockk()

            application {
                install(ContentNegotiation) {
                    json()
                }
                configureHealthRouting(
                    "v1.0.0",
                    appService,
                    mockPrometheusMeterRegistry,
                    mockHealthSystem,
                    mockHealthService,
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

            val readyResponse = client.get("/hams/ready")
            assertEquals(HttpStatusCode.OK, readyResponse.status)
        }

    @Test
    fun healthService(): Unit =
        runBlocking {
            // Test startup and shutdown
            // Test simple http working for version endpoint
            val appService: AppService = mockk(relaxed = true)
            val mockMetricsRegistry: PrometheusMeterRegistry = mockk(relaxed = true)
            val mockHealthSystem: HealthSystem = spyk(HealthSystem())

            // registerAlive and deregisterAlive are open so they should work with spyk

            val version = "v1.0.0"

            val healthService =
                HealthService(
                    version,
                    appService,
                    mockMetricsRegistry,
                    mockHealthSystem,
                )
            val healthJob =
                launch {
                    println("starting health service")
                    healthService.startSuspended()
                    println("health service stopped")
                }
            val healthPort = 8079

            // Wait for the server to be fully started using the new signaling mechanism
            healthService.waitUntilStarted()

            val response = HttpClient(CIO).get("http://localhost:$healthPort/hams/version")

            Assert.assertEquals(HttpStatusCode.OK, response.status)
            Assert.assertEquals(version, response.bodyAsText())

            healthService.stop()
            healthJob.join()
            println("Done with test")
        }
}
