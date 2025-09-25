package com.polecatworks.kotlin.k8smicro

import com.polecatworks.kotlin.k8smicro.app.AppService
import com.polecatworks.kotlin.k8smicro.app.configureAppRouting
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals

class AppApiTest {
    @Test
    fun embeddedHealthApi() =
        testApplication {
            val mockAppService: AppService = mockk()
            every { mockAppService.config.webserver.prefix } returns "/k8s-micro/v0"
            every { mockAppService.state.count.incrementAndGet() } returns 3

            application {
                install(ContentNegotiation) {
                    json()
                }
                configureAppRouting(mockAppService)
            }

            run {
                val response = client.get("/")
                assertEquals(HttpStatusCode.NotFound, response.status)
                println("$response, ${response.bodyAsText()}, ${response.headers}")
            }
            run {
                val response = client.get("/k8s-micro/v0")
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("{\"str\":\"params.a\"}", response.bodyAsText())
                println("$response, ${response.bodyAsText()}, ${response.headers}")
            }
            run {
                val response = client.get("/k8s-micro/v0/")
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("{\"str\":\"params.a\"}", response.bodyAsText())
                println("$response, ${response.bodyAsText()}, ${response.headers}")
            }

            run {
                val response = client.get("/string/apple")
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("Smokeapple", response.bodyAsText())
                println("$response, ${response.bodyAsText()}, ${response.headers}")
            }
            run {
                val response = client.get("/k8s-micro/v0/count")
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("{\"count\":3}", response.bodyAsText())
                println("$response, ${response.bodyAsText()}, ${response.headers}")
            }

            println("API is working")
        }
}
