package com.polecatworks.kotlin.k8smicro

import com.polecatworks.kotlin.k8smicro.plugins.configureHealthRouting
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.testing.*
import org.junit.Test
import kotlin.test.assertEquals

class HelloTest {

    @Test
    fun testEmbedded() = testApplication {
        application {
            install(ContentNegotiation) {
                json()
            }
            configureHealthRouting(HealthSystem())
        }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello World!", response.bodyAsText())

        var alive_response = client.get("/health/alive")
        assertEquals(HttpStatusCode.OK, alive_response.status)
        assertEquals("{\"id\":1,\"firstName\":\"Ben\",\"lastName\":\"Greene\"}", alive_response.bodyAsText())
    }
}
