package com.polecatworks.kotlin.k8smicro

import com.polecatworks.kotlin.k8smicro.plugins.configureHealthRouting
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.testing.*
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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
        val dut = HealthCheck("dut", marginTime)
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
    fun testHealthSystem() {
        // Check register, deRegister
        // Check times both in margin, 1 in margin and after removing entry
        // Check no items in health list
        val hs = HealthSystem()

        val marginTimeShort = 500.milliseconds
        val marginTimeLong = 1.seconds

        val myHealthShort = HealthCheck("short", marginTimeShort)
        val myHealthLong = HealthCheck("long", marginTimeLong)

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
    fun mockTestPrometheusMetricsRegistry() {
        val mockPrometheusMeterRegistry: PrometheusMeterRegistry = mock()
        whenever(mockPrometheusMeterRegistry.scrape()).thenReturn("My Metrics")
        val mymetrics = mockPrometheusMeterRegistry.scrape()
        verify(mockPrometheusMeterRegistry).scrape()
    }

    @Test
    fun mockkTest() {
        val mockPrometheusMeterRegistry = mockk<PrometheusMeterRegistry>()
        every { mockPrometheusMeterRegistry.scrape() } returns "My Metrics"
        assertEquals("My Metrics", mockPrometheusMeterRegistry.scrape())
        io.mockk.verify {
            mockPrometheusMeterRegistry.scrape()
        }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun mockTestHealth() {
        val mockHealthSystem: HealthSystem = mockk()
        every { mockHealthSystem.checkAlive(any()) } returns HealthSystemResult("alive", true, listOf())
        val myResult = mockHealthSystem.checkAlive(markNow())
        io.mockk.verify {
            mockHealthSystem.checkAlive(any())
        }
        println("ALl is klar")
    }

//    @OptIn(ExperimentalTime::class)
//    @Test
//    fun mockTestHealthSystem() {
//        assertFailsWith<NullPointerException> {
//            val mockHealthSystem: HealthSystem = mock()
//            whenever(mockHealthSystem.checkAlive(argThat { true })).thenReturn(
//                HealthSystemResult(
//                    "alive",
//                    true,
//                    listOf()
//                )
//            )
//
//            val myResult = mockHealthSystem.checkAlive(markNow())
//            verify(mockHealthSystem).checkAlive(argThat { true })
//        }
//    }

    @Test
    fun testEmbedded() = testApplication {
        val mockPrometheusMeterRegistry: PrometheusMeterRegistry = mock()
        whenever(mockPrometheusMeterRegistry.scrape()).thenReturn("My Metrics")

        // TODO: Mock HealthSystem once it is no longer Kotlin Experimental
        application {
            install(ContentNegotiation) {
                json()
            }
            configureHealthRouting(HealthSystem(), mockPrometheusMeterRegistry)
        }

        val metricsResponse = client.get("/hams/metrics")
        assertEquals(HttpStatusCode.OK, metricsResponse.status)
        verify(mockPrometheusMeterRegistry).scrape()
        assertEquals("My Metrics", metricsResponse.bodyAsText())

        val aliveResponse = client.get("/hams/alive")
        assertEquals(HttpStatusCode.OK, aliveResponse.status)

        val readyResponse = client.get("/hams/ready")
        assertEquals(HttpStatusCode.OK, readyResponse.status)
    }
}
