package com.polecatworks.kotlin.k8smicro

import com.github.avrokotlin.avro4k.Avro
import com.github.avrokotlin.avro4k.schema
import com.polecatworks.kotlin.k8smicro.eventSerde.Event
import com.polecatworks.kotlin.k8smicro.eventSerde.EventDeSerializer
import com.polecatworks.kotlin.k8smicro.eventSerde.EventSchemaManager
import com.polecatworks.kotlin.k8smicro.eventSerde.EventSerializer
import com.polecatworks.kotlin.k8smicro.health.HealthSystem
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import org.junit.Ignore
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

val schemaRegistryUrl = "http://localhost:8081"

class Avro4k {
    val schemaMap =
        mapOf(
            "Chaser" to 1,
            "Aggregate" to 2,
            "Bill" to 3,
            "PaymentRequest" to 4,
            "PaymentFailed" to 5,
            "BillAggregate" to 6,
        )

    val chaser =
        Event.Chaser(
            name = "Ben",
            id = "Benid",
            sent = 3,
            ttl = 4,
            previous = null,
        )

    val bill =
        Event.Bill(
            billId = "BILL-1",
            customerId = "CUST-1",
            orderId = "ORDER-1",
            amountCents = 10000,
            currency = "USD",
            issuedAt = 1000L,
            dueDate = 2000L,
        )

    val paymentRequest =
        Event.PaymentRequest(
            paymentId = "PAY-1",
            billId = "BILL-1",
            customerId = "CUST-1",
            amountCents = 10000,
            currency = "USD",
            requestedAt = 1100L,
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
    fun writeReadAvro() {
        fun writeSchemaId(
            out: ByteArrayOutputStream,
            id: Int,
        ) {
            out.write(0)
            out.write(ByteBuffer.allocate(4).putInt(id).array())
        }

        val schema = Avro.schema<Event.Chaser>()
        println(schema.toString(true))

        val out = ByteArrayOutputStream()

        writeSchemaId(out, 3)
        val bytes = out.toByteArray()
        out.close()

        println("length is ${bytes.size}")

        assertEquals(5, bytes.size) { "magic byte + SchemaID should equal 5" }

        val chaserBytes = Avro.encodeToByteArray(chaser)

        println("Chaser = $chaserBytes, length ${chaserBytes.size}")

        val obj = Avro.decodeFromByteArray<Event.Chaser>(chaserBytes)
        println(obj)
    }

    @Test
    fun testSealedClass() {
        fun getChaser(): Event = chaser

        val myChaser = getChaser()

        when (myChaser) {
            is Event.Chaser -> println("Chaser")
            is Event.Aggregate -> println("Aggregate")
            is Event.Bill -> println("Bill")
            is Event.PaymentRequest -> println("PaymentRequest")
            is Event.PaymentFailed -> println("PaymentFailed")
            is Event.BillAggregate -> println("BillAggregate")
            else -> throw AssertionError("Unknown event type: ${myChaser.javaClass.name}")
        }

        println("Chaser = $myChaser")

        val x = Event.subClasses()

        println(x)
    }

    // Tests start here

    @Test
    fun avroSerdesOnTestStructures() {
        val billBytes = Avro.encodeToByteArray(bill)

        println("Bill = $billBytes, length ${billBytes.size}")

        val obj = Avro.decodeFromByteArray<Event.Bill>(billBytes)

        assertEquals(bill, obj) { "Original and deserialized should match" }
        println(obj)
    }

    @Test
    fun eventSerializer() {
        val events = listOf(bill, paymentRequest)

        val eventSchemaManager = EventSchemaManager(schemaRegistryApi)

        val schemaIdBill = 3
        eventSchemaManager.registerSchema(Event.Bill::class.java, schemaIdBill)
        val schemaIdPayReq = 4
        eventSchemaManager.registerSchema(Event.PaymentRequest::class.java, schemaIdPayReq)

        val eventSerializer = EventSerializer(eventSchemaManager)

        val serializeds = events.map { event -> eventSerializer.serialize("topic", event) }

        for (event in serializeds) {
            println("Size = ${event?.size}")
        }

        val billSerialized = eventSerializer.serialize("apple", bill)

        assertNotNull(billSerialized) { "serializer is not null" }

        println("serialized event = $billSerialized of length ${billSerialized.size}")
    }

    @Test
    fun eventDeserializer() {
        val events = listOf(bill, paymentRequest)

        val eventSchemaManager = EventSchemaManager(schemaRegistryApi)

        val schemaIdBill = 3
        eventSchemaManager.registerSchema(Event.Bill::class.java, schemaIdBill)
        val schemaIdPayReq = 4
        eventSchemaManager.registerSchema(Event.PaymentRequest::class.java, schemaIdPayReq)

        val eventSerializer = EventSerializer(eventSchemaManager)

        val serializeds = events.map { event -> eventSerializer.serialize("topic", event) }

        val eventDeSerializer = EventDeSerializer(eventSchemaManager)

        val deserializedEvents = serializeds.map { eventBytes -> eventDeSerializer.deserialize("topic", eventBytes) }

        assertEquals(events, deserializedEvents)
    }

    @Ignore("Need network enabled for this")
    @Test
    fun getIdFromSchemaRegistry() =
        runBlocking {
        /* Identify url for schema registry
         * Create the schema you want to register and the topic you want to register against
         * Make call to the schema registry to register the schema
         * If we get a bad reply then take an error
         * If we get a good reply then capture the ID of the schema and then use it for subsequent
         * writes to kafka.
         *
         */

            val running = AtomicBoolean()
            val health = HealthSystem()
            val schemaServer =
                KafkaSchemaRegistryApi(KafkaSchemaRegistryConfig(schemaRegistryUrl, 60.seconds), CIO.create(), health, running)

            val reply = schemaServer.checkConnection()

            assert(reply) { "Could not connect to schema registry" }

            val schema = Avro.schema<Event.Bill>()

            val schemaId = schemaServer.registerSchema("test001", schema.toString())
            assertEquals(3, schemaId)
        }

    @Test
    fun getIdFromSchemaRegistryMocked() =
        runBlocking {
        /* Identify url for schema registry
         * Create the schema you want to register and the topic you want to register against
         * Make call to the schema registry to register the schema
         * If we get a bad reply then take an error
         * If we get a good reply then capture the ID of the schema and then use it for subsequent
         * writes to kafka.
         *
         */

            val schemaServer =
                KafkaSchemaRegistryApi(KafkaSchemaRegistryConfig("http://localhost:8082", 60.seconds), mockEngine, health, running)

            assertEquals(true, schemaServer.checkConnection())

            val schema = Avro.schema<Event.Bill>()

            // Expecting to use test001-value structure for schema names where test001 is the topic name
            val schemaId = schemaServer.registerSchema("test001-value", schema.toString())
            assertEquals(3, schemaId)
        }

    // Test that loops over each of the subclasses fo Event and registers their schemas
    @OptIn(InternalSerializationApi::class)
    @Test
    fun registerAllEventSchemas() =
        runBlocking {
            val schemaServer =
                KafkaSchemaRegistryApi(KafkaSchemaRegistryConfig("http://localhost:8082", 60.seconds), mockEngine, health, running)

            val myMap =
                Event.subClasses().iterator().asSequence().associate { myClass ->
                    val schema = Avro.schema(myClass.serializer().descriptor)

                    val schemaId = schemaServer.registerSchema("test001-value", schema.toString())

                    myClass.simpleName!! to schemaId
                }

            assertEquals(schemaMap, myMap)
        }

    // Test EventSchemaManager to confirm init + adding schema and reading a schema via id or class
    @Test
    fun testEventSchemaManager() {
        val eventSchemaManager = EventSchemaManager(schemaRegistryApi)

        // Register a schema
        val schemaIdBill = 3
        eventSchemaManager.registerSchema(Event.Bill::class.java, schemaIdBill)
        val schemaIdPayReq = 4
        eventSchemaManager.registerSchema(Event.PaymentRequest::class.java, schemaIdPayReq)

        // Retrieve schema by class
        val retrievedSchemaIdBill = eventSchemaManager.getSchemaIdForClass(Event.Bill::class.java)
        assertEquals(schemaIdBill, retrievedSchemaIdBill)

        // Retrieve class by schema id
        val retrievedClassBill = eventSchemaManager.getClassForSchemaId(schemaIdBill)
        assertEquals(Event.Bill::class.java, retrievedClassBill)

        // Retrieve schema by class
        val retrievedSchemaIdPayReq = eventSchemaManager.getSchemaIdForClass(Event.PaymentRequest::class.java)
        assertEquals(schemaIdPayReq, retrievedSchemaIdPayReq)

        // Retrieve class by schema id
        val retrievedClassPayReq = eventSchemaManager.getClassForSchemaId(schemaIdPayReq)
        assertEquals(Event.PaymentRequest::class.java, retrievedClassPayReq)

        val bId = eventSchemaManager.getSchemaIdForEvent(bill)
        assertEquals(schemaIdBill, bId)

        val pId = eventSchemaManager.getSchemaIdForEvent(paymentRequest)

        assertEquals(schemaIdPayReq, pId)
    }

    @Test
    fun testEventSchemaManagerAuto() {
        val eventSchemaManager = EventSchemaManager(schemaRegistryApi)
        runBlocking {
            eventSchemaManager.registerAllSchemas("test001-value")
        }

        val schemaIdBill = 3
        val schemaIdPayReq = 4

        // Retrieve schema by class
        val retrievedSchemaIdBill = eventSchemaManager.getSchemaIdForClass(Event.Bill::class.java)
        assertEquals(schemaIdBill, retrievedSchemaIdBill)

        // Retrieve class by schema id
        val retrievedClassBill = eventSchemaManager.getClassForSchemaId(schemaIdBill)
        assertEquals(Event.Bill::class.java, retrievedClassBill)

        // Retrieve schema by class
        val retrievedSchemaIdPayReq = eventSchemaManager.getSchemaIdForClass(Event.PaymentRequest::class.java)
        assertEquals(schemaIdPayReq, retrievedSchemaIdPayReq)

        // Retrieve class by schema id
        val retrievedClassPayReq = eventSchemaManager.getClassForSchemaId(schemaIdPayReq)
        assertEquals(Event.PaymentRequest::class.java, retrievedClassPayReq)

        val bId = eventSchemaManager.getSchemaIdForEvent(bill)
        assertEquals(schemaIdBill, bId)

        val pId = eventSchemaManager.getSchemaIdForEvent(paymentRequest)

        assertEquals(schemaIdPayReq, pId)

        println(eventSchemaManager)
    }
}
