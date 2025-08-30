package com.polecatworks.kotlin.k8smicro

import com.github.avrokotlin.avro4k.Avro

import com.github.avrokotlin.avro4k.schema
import com.polecatworks.kotlin.k8smicro.health.HealthSystem
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking

import com.polecatworks.kotlin.k8smicro.eventSerde.Ingredient
import com.polecatworks.kotlin.k8smicro.eventSerde.Event
import com.polecatworks.kotlin.k8smicro.eventSerde.EventSerializer
import com.polecatworks.kotlin.k8smicro.eventSerde.EventDeSerializer


import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.Ignore
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds
import kotlin.test.assertNotNull


val schemaRegistryUrl = "http://localhost:8081"

class Avro4k {



    @Test
    fun writeReadAvro() {
        fun writeSchemaId(out: ByteArrayOutputStream, id: Int) {
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

        assertEquals(5,bytes.size) {"magic byte + SchemaID should equal 5"}

        // 1. Create a Margherita Pizza object
        val margherita = Event.Pizza(
            name = "Margherita",
            ingredients = listOf(
                Ingredient("Dough", 3.0, 5.0),
                Ingredient("Tomato Sauce", 8.0, 1.0),
                Ingredient("Mozzarella", 1.0, 22.0),
                Ingredient("Basil", 0.0, 0.1)
            ),
            vegetarian = true,
            kcals = 800
        )

        // 2. Create a Pepperoni Pizza object
        val pepperoni = Event.Pizza(
            name = "Pepperoni",
            ingredients = listOf(
                Ingredient("Dough", 3.0, 5.0),
                Ingredient("Tomato Sauce", 8.0, 1.0),
                Ingredient("Mozzarella", 1.0, 22.0),
                Ingredient("Pepperoni", 0.5, 40.0)
            ),
            vegetarian = false,
            kcals = 1200
        )

        // 3. Create a Veggie Supreme Pizza object
        val veggieSupreme = Event.Pizza(
            name = "Veggie Supreme",
            ingredients = listOf(
                Ingredient("Dough", 3.0, 5.0),
                Ingredient("Tomato Sauce", 8.0, 1.0),
                Ingredient("Mozzarella", 1.0, 22.0),
                Ingredient("Bell Peppers", 4.0, 0.3),
                Ingredient("Onions", 5.0, 0.1),
                Ingredient("Mushrooms", 2.0, 0.5)
            ),
            vegetarian = true,
            kcals = 950
        )


        val margherita_bytes = Avro.encodeToByteArray(margherita)

        println("Margarita = ${margherita_bytes}, length ${margherita_bytes.size}")

        val obj = Avro.decodeFromByteArray<Event.Pizza>(margherita_bytes)
        println(obj)
    }


    val margherita = Event.Pizza(
        name = "Margherita",
        ingredients = listOf(
            Ingredient("Dough", 3.0, 5.0),
            Ingredient("Tomato Sauce", 8.0, 1.0),
            Ingredient("Mozzarella", 1.0, 22.0),
            Ingredient("Basil", 0.0, 0.1)
        ),
        vegetarian = true,
        kcals = 800
    )

    val burger = Event.Burger(
        name = "CheeseBurger",
        ingredients = listOf(
            Ingredient("Bread", 3.0, 5.0),
            Ingredient("Meat", 8.0, 1.0),
        ),
        kcals = 200
    )



    @Test
    fun testSealedClass() {

        fun getFood(): Event {
            return margherita
        }

        val myFood = getFood()

        when (myFood) {
            is Event.Burger -> println("BURGER")
            is Event.Pizza -> println("PIZZA")
        }

        println("food = $myFood")
    }




    // Tests start here

    @Test
    fun avroSerdesOnTestStructures() {

        val margherita_bytes = Avro.encodeToByteArray(margherita)

        println("Margarita = ${margherita_bytes}, length ${margherita_bytes.size}")

        val obj = Avro.decodeFromByteArray<Event.Pizza>(margherita_bytes)

        assertEquals(margherita, obj) {"Original and deserialized should match"}
        println(obj)
    }


    @Test
    fun eventSerializer() {
        val foods = listOf(margherita, burger)

        val eventSerializer = EventSerializer()

        val serializeds = foods.map{food -> eventSerializer.serialize("topic", food)}

        for (food in serializeds) {
            println("Size = ${food?.size}")
        }

        assert(serializeds[0]?.size==121) {"Margherita should be 121 bytes"}
        assert(serializeds[1]?.size==65) {"Burger should be 65 bytes"}

        val margherita_serialized = eventSerializer.serialize("apple",margherita)

        assertNotNull(margherita_serialized) {"serializer is not null"}

        println("serialized food = $margherita_serialized of length ${margherita_serialized.size}")
    }


    @Test
    fun eventDeserializer() {
        val foods = listOf(margherita, burger)

        val eventSerializer = EventSerializer()

        val serializeds = foods.map{food -> eventSerializer.serialize("topic", food)}

        val eventDeSerializer = EventDeSerializer()

        val deserializedFoods = serializeds.map{ foodBytes -> eventDeSerializer.deserialize("topic", foodBytes)}

        assertEquals(foods, deserializedFoods)
    }




    @Ignore("Need network enabled for this")
    @Test
    fun getIdFromSchemaRegistry() = runBlocking {
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
        val schemaServer = KafkaSchemaRegistryApi(KafkaSchemaRegistryConfig(schemaRegistryUrl, 60.seconds), CIO.create(), health, running)

        val reply = schemaServer.checkConnection()

        assert(reply) {"Could not connect to schema registry"}


        @Serializable
        data class Ingredient(val name: String, val sugar: Double, val fat: Double)

        @Serializable
        data class Pizza(val name: String, val ingredients: List<Ingredient>, val vegetarian: Boolean, val kcals: Int)

        val schema = Avro.schema<Pizza>()

        val schemaId = schemaServer.registerSchema("test001", schema.toString())
        assertEquals(1, schemaId)
    }

    @Test
    fun getIdFromSchemaRegistryMocked() = runBlocking {
        /* Identify url for schema registry
         * Create the schema you want to register and the topic you want to register against
         * Make call to the schema registry to register the schema
         * If we get a bad reply then take an error
         * If we get a good reply then capture the ID of the schema and then use it for subsequent
         * writes to kafka.
         *
         */

        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel("""{"compatibilityLevel": "FULL"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val running = AtomicBoolean()
        val health = HealthSystem()
        val apiClient = KafkaSchemaRegistryApi(KafkaSchemaRegistryConfig("http://localhost:8082", 60.seconds), mockEngine, health, running)

        assertEquals(true, apiClient.checkConnection())


    }
}
