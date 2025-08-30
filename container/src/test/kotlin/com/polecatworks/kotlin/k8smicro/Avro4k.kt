package com.polecatworks.kotlin.k8smicro

import com.github.avrokotlin.avro4k.Avro
import com.github.avrokotlin.avro4k.schema
import com.polecatworks.kotlin.k8smicro.eventSerde.Event
import com.polecatworks.kotlin.k8smicro.eventSerde.EventDeSerializer
import com.polecatworks.kotlin.k8smicro.eventSerde.EventSerializer
import com.polecatworks.kotlin.k8smicro.eventSerde.Ingredient
import com.polecatworks.kotlin.k8smicro.health.HealthSystem
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
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

    // 2. Create a Pepperoni Pizza object
    val pepperoni =
        Event.Pizza(
            name = "Pepperoni",
            ingredients =
                listOf(
                    Ingredient("Dough", 3.0, 5.0),
                    Ingredient("Tomato Sauce", 8.0, 1.0),
                    Ingredient("Mozzarella", 1.0, 22.0),
                    Ingredient("Pepperoni", 0.5, 40.0),
                ),
            vegetarian = false,
            kcals = 1200,
        )

    // 3. Create a Veggie Supreme Pizza object
    val veggieSupreme =
        Event.Pizza(
            name = "Veggie Supreme",
            ingredients =
                listOf(
                    Ingredient("Dough", 3.0, 5.0),
                    Ingredient("Tomato Sauce", 8.0, 1.0),
                    Ingredient("Mozzarella", 1.0, 22.0),
                    Ingredient("Bell Peppers", 4.0, 0.3),
                    Ingredient("Onions", 5.0, 0.1),
                    Ingredient("Mushrooms", 2.0, 0.5),
                ),
            vegetarian = true,
            kcals = 950,
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

    @Test
    fun writeReadAvro() {
        fun writeSchemaId(
            out: ByteArrayOutputStream,
            id: Int,
        ) {
            out.write(0)
            out.write(ByteBuffer.allocate(4).putInt(id).array())
        }

        val schema = Avro.schema<Event.Pizza>()
        println(schema.toString(true))

        val out = ByteArrayOutputStream()

        writeSchemaId(out, 3)
        val bytes = out.toByteArray()
        out.close()

        println("length is ${bytes.size}")

        assertEquals(5, bytes.size) { "magic byte + SchemaID should equal 5" }

        val margheritaBytes = Avro.encodeToByteArray(margherita)

        println("Margarita = $margheritaBytes, length ${margheritaBytes.size}")

        val obj = Avro.decodeFromByteArray<Event.Pizza>(margheritaBytes)
        println(obj)
    }

    @Test
    fun testSealedClass() {
        fun getFood(): Event = margherita

        val myFood = getFood()

        when (myFood) {
            is Event.Burger -> println("BURGER")
            is Event.Pizza -> println("PIZZA")
        }

        println("food = $myFood")

        val x = Event.subClasses()

        println(x)
    }

    // Tests start here

    @Test
    fun avroSerdesOnTestStructures() {
        val margheritaBytes = Avro.encodeToByteArray(margherita)

        println("Margarita = $margheritaBytes, length ${margheritaBytes.size}")

        val obj = Avro.decodeFromByteArray<Event.Pizza>(margheritaBytes)

        assertEquals(margherita, obj) { "Original and deserialized should match" }
        println(obj)
    }

    @Test
    fun eventSerializer() {
        val foods = listOf(margherita, burger)

        val eventSerializer = EventSerializer()

        val serializeds = foods.map { food -> eventSerializer.serialize("topic", food) }

        for (food in serializeds) {
            println("Size = ${food?.size}")
        }

        assert(serializeds[0]?.size == 121) { "Margherita should be 121 bytes" }
        assert(serializeds[1]?.size == 65) { "Burger should be 65 bytes" }

        val margheritaSerialized = eventSerializer.serialize("apple", margherita)

        assertNotNull(margheritaSerialized) { "serializer is not null" }

        println("serialized food = $margheritaSerialized of length ${margheritaSerialized.size}")
    }

    @Test
    fun eventDeserializer() {
        val foods = listOf(margherita, burger)

        val eventSerializer = EventSerializer()

        val serializeds = foods.map { food -> eventSerializer.serialize("topic", food) }

        val eventDeSerializer = EventDeSerializer()

        val deserializedFoods = serializeds.map { foodBytes -> eventDeSerializer.deserialize("topic", foodBytes) }

        assertEquals(foods, deserializedFoods)
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

            val schema = Avro.schema<Event.Pizza>()

            val schemaId = schemaServer.registerSchema("test001", schema.toString())
            assertEquals(1, schemaId)
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
                            respond(
                                content = ByteReadChannel("""{"id": 1}"""),
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
            val schemaServer =
                KafkaSchemaRegistryApi(KafkaSchemaRegistryConfig("http://localhost:8082", 60.seconds), mockEngine, health, running)

            assertEquals(true, schemaServer.checkConnection())

            val schema = Avro.schema<Event.Pizza>()

            // Expecting to use test001-value structure for schema names where test001 is the topic name
            val schemaId = schemaServer.registerSchema("test001-value", schema.toString())
            assertEquals(1, schemaId)
        }

    // Test that loops over each of the subclasses fo Event and registers their schemas
    @OptIn(InternalSerializationApi::class)
    @Test
    fun registerAllEventSchemas() =
        runBlocking {
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
                            respond(
                                content = ByteReadChannel("""{"id": 1}"""),
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
            val schemaServer =
                KafkaSchemaRegistryApi(KafkaSchemaRegistryConfig("http://localhost:8082", 60.seconds), mockEngine, health, running)

            for (myClass in Event.subClasses()) {
                val schema = Avro.schema(myClass.serializer().descriptor)

                val schemaId = schemaServer.registerSchema("test001-value", schema.toString())
                assertEquals(1, schemaId)
            }
        }
}
