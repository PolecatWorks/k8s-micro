package com.polecatworks.kotlin.k8smicro

import com.polecatworks.kotlin.k8smicro.eventSerde.Event
import com.polecatworks.kotlin.k8smicro.eventSerde.EventSchemaManager
import com.polecatworks.kotlin.k8smicro.eventSerde.EventSerde
import com.polecatworks.kotlin.k8smicro.health.HealthSystem
import io.ktor.client.engine.mock.MockEngine
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
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class KafkaSerdeTest {
    val schemaMap =
        mapOf(
            "Bill" to 1,
            "PaymentRequest" to 2,
            "Chaser" to 3,
            "Aggregate" to 4,
            "PaymentFailed" to 5,
            "BillAggregate" to 6,
        )

    val myBill =
        Event.Bill(
            billId = "BILL-1",
            customerId = "CUST-1",
            orderId = "ORDER-1",
            amountCents = 10000,
            currency = "USD",
            issuedAt = 1000L,
            dueDate = 2000L,
        )

//    val burger =
//        Event.Burger(
//            name = "CheeseBurger",
//            ingredients =
//                listOf(
//                    Ingredient("Bread", 3.0, 5.0),
//                    Ingredient("Meat", 8.0, 1.0),
//                ),
//            kcals = 200,
//        )

    val schemaConfig = KafkaSchemaRegistryConfig(schemaRegistryUrl, 60.seconds)
    val mockEngine =
        MockEngine { request ->
            println(request)
            when {
                request.url.encodedPath == "/config" -> {
                    respond(
                        content = ByteReadChannel("""{"compatibilityLevel": "FULL"}"""),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }

//                request.url.encodedPath.matches(Regex("\\\\/subjects\\\\/test001-value-com\\.polecatworks\\.chaser\\..*\\\\/versions")) -> {
                request.url.encodedPath.matches(Regex("/subjects/test001-value-com.*/versions")) -> {

                    val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject

                    val schema =
                        when (val value = body["schema"]) {
                            is JsonPrimitive -> value.content
                            else -> throw IllegalArgumentException("schema not defined in request")
                        }

                    val schemaContent = Json.parseToJsonElement(schema).jsonObject

                    val schemaName =
                        when (val value = schemaContent["name"]) {
                            is JsonPrimitive -> value.content
                            else -> throw IllegalArgumentException("name not defined in request")
                        }

                    val schemaId = schemaMap[schemaName]!!

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
        KafkaSchemaRegistryApi(schemaConfig, mockEngine, health, running)

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

        val serde = EventSerde()
        serde.setSchemaManager(eventSchemaManager)
        val serialized = serde.serializer().serialize("test001-value", myBill)

        val deserialized = serde.deserializer()!!.deserialize("test001-value", serialized)
        assertEquals(myBill, deserialized, "Deserialized event does not match original")
    }

    @Test
    fun kafkaChaserSerde() {
        val eventSchemaManager = EventSchemaManager(schemaRegistryApi)
        runBlocking {
            eventSchemaManager.registerAllSchemas("test001-value")
        }

        val myChaser = Event.Chaser("Peacock", "id0", 123, 22, null)

        val serde = EventSerde()
        serde.setSchemaManager(eventSchemaManager)

        val serialized = serde.serializer().serialize("test001-value", myChaser)

        val deserialized = serde.deserializer()!!.deserialize("test001-value", serialized)
        assert(myChaser == deserialized) { "Deserialized event does not match original" }

/*
        val genericAvroSerde = GenericAvroSerde()

        genericAvroSerde.configure(
            mapOf(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to schemaRegistryApi.schemaRegistryUrl),
            false,
        )

        val genericDeserialized = genericAvroSerde.deserializer().deserialize("test001-value", serialized)

        assertEquals(myChaser.name, genericDeserialized.get("name"))
*/
    }
}
