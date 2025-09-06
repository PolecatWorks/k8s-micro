package com.polecatworks.kotlin.k8smicro

import com.polecatworks.kotlin.k8smicro.eventSerde.Event
import com.polecatworks.kotlin.k8smicro.eventSerde.EventSchemaManager
import com.polecatworks.kotlin.k8smicro.eventSerde.EventSerde
import com.polecatworks.kotlin.k8smicro.eventSerde.Ingredient
import com.polecatworks.kotlin.k8smicro.health.HealthSystem
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngine.Companion.invoke
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class KafkaSerdeTest {
    val schemaMap = mapOf("Burger" to 2, "Pizza" to 1)

    // 1. Create a Margherita Pizza object
    val margherita =
        Event.Pizza(
            name = "Margherita",
            ingredients =
                listOf(
                    Ingredient("Dough", 3.0, 5.0),
                    Ingredient("Tomato Sauce", 8.0, 1.0),
                    Ingredient("Mozzarella", 1.0, 22.0),
                    Ingredient("Basil", 0.0, 0.1),
                ),
            vegetarian = true,
            kcals = 800,
        )

    val burger =
        Event.Burger(
            name = "CheeseBurger",
            ingredients =
                listOf(
                    Ingredient("Bread", 3.0, 5.0),
                    Ingredient("Meat", 8.0, 1.0),
                ),
            kcals = 200,
        )

    val schmeaConfig = KafkaSchemaRegistryConfig(schemaRegistryUrl, 60.seconds)
    val mockEngine =
        MockEngine { request ->
            println(request)
            when (request.url.encodedPath) {
                "/config" -> {
                    respond(
                        content = ByteReadChannel("""{"compatibilityLevel": "FULL"}"""),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

                "/subjects/test001-value/versions" -> {

                    val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject

                    val schema =
                        when (val value = body["schema"]) {
                            is JsonPrimitive -> value.content
                            else -> ""
                        }

                    val schemaContent = Json.parseToJsonElement(schema).jsonObject

                    val schemaName =
                        when (val value = schemaContent["name"]) {
                            is JsonPrimitive -> value.content
                            else -> ""
                        }

                    val schemaId = schemaMap[schemaName]

                    respond(
                        content = ByteReadChannel("""{"id": $schemaId}"""),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> {
                    respond(
                        content = ByteReadChannel("""{"error_code":404,"message":"Not found"}"""),
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }
        }
    val running = AtomicBoolean()
    val health = HealthSystem()

    val schemaRegistryApi =
        KafkaSchemaRegistryApi(schmeaConfig, mockEngine, health, running)

    @Test
    fun basicTrigger() {
        assert(true) { "This should never fail" }
    }

    @Test
    fun kafkaSerde() {
        val eventSchemaManager = EventSchemaManager(schemaRegistryApi)
        runBlocking {
            eventSchemaManager.registerAllSchemas("test001-value")
        }

        val serde = EventSerde<Event>(eventSchemaManager)
        val serialized = serde.serializer().serialize("test001-value", margherita)

        val deserialized = serde.deserializer().deserialize("test001-value", serialized)
        assert(margherita == deserialized) { "Deserialized event does not match original" }
    }
}
