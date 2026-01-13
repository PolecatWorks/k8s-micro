package com.polecatworks.kotlin.k8smicro

import com.polecatworks.kotlin.k8smicro.app.AppService
import com.polecatworks.kotlin.k8smicro.app.configureAppRouting
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class AppApiTest {
    @Test
    fun testAppRouting() =
        testApplication {
            val myConfig =
                K8sMicroConfig(
                    env = "test",
                    webserver = WebServer(port = 8080, prefix = "/k8s-micro/v0"),
                    randomThread = RandomThread(1.seconds),
                    app = K8sMicroApp(1.seconds),
                    kafkaProcessor =
                        KafkaProcessorConfig(
                            hostUrl = "http://localhost:8080",
                            schemaRegistry = KafkaSchemaRegistryConfig("http://localhost:8081", 60.seconds),
                            readTopic = "read",
                            writeTopic = "write",
                            billingOutputTopic = "billing",
                            applicationId = "app",
                            autoOffsetReset = "earliest",
                        ),
                    sqlServer =
                        SqlServerConfig(
                            database = SqlServerDatabase("jdbc:postgresql://localhost:5432/test", "sa", ""),
                            driver = "org.postgresql.Driver",
                            healthSleep = 10.seconds,
                            threadSleep = 1.seconds,
                        ),
                )
            val mockAppService: AppService = spyk(AppService(mockk(relaxed = true), mockk(relaxed = true), myConfig))

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
                val response = client.get("/k8s-micro/v0/string/apple")
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("Smokeapple", response.bodyAsText())
                println("$response, ${response.bodyAsText()}, ${response.headers}")
            }
            run {
                val response = client.get("/k8s-micro/v0/count")
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("{\"count\":1}", response.bodyAsText())
                println("$response, ${response.bodyAsText()}, ${response.headers}")
            }

            println("API is working")
        }
}
