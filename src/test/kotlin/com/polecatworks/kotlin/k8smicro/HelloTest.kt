package com.polecatworks.kotlin.k8smicro

import com.polecatworks.kotlin.k8smicro.plugins.configureRouting
import org.junit.Test
import kotlin.test.assertEquals
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.testing.*

class HelloTest {

    @Test
    fun testEmbeddeded() = testApplication {
        application{
            install(ContentNegotiation) {
                json()
            }
            configureRouting()
        }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello World!", response.bodyAsText())
    }



}
