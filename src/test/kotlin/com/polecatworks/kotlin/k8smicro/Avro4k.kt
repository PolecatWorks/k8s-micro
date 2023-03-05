package com.polecatworks.kotlin.k8smicro

import com.github.avrokotlin.avro4k.Avro
import com.github.avrokotlin.avro4k.serializer.LocalDateSerializer
import com.polecatworks.kotlin.k8smicro.health.HealthSystem
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class Avro4k {

    //    How to do avro on kafka  https://github.com/thake/avro4k-kafka-serializer/tree/main/src

    @Test
    fun createAccess() {
        fun writeSchemaId(out: ByteArrayOutputStream, id: Int) {
            out.write(0)
            out.write(ByteBuffer.allocate(4).putInt(id).array())
        }

        @Serializable
        data class Ingredient(val name: String, val sugar: Double, val fat: Double)

        @Serializable
        data class Pizza(val name: String, val ingredients: List<Ingredient>, val vegetarian: Boolean, val kcals: Int)

        val schema = Avro.default.schema(Pizza.serializer())
        println(schema.toString(true))

        @Serializable
        data class StockPurchase(
            val ticker: String = "",
            @Serializable(with = LocalDateSerializer::class) val purchaseDate: LocalDate = LocalDate.now()
        )

        val stockPurchase = StockPurchase("apple")

        val avroRecord = Avro.default.toRecord(StockPurchase.serializer(), stockPurchase)

        val out = ByteArrayOutputStream()

        writeSchemaId(out, 3)
        val bytes = out.toByteArray()
        out.close()

        println("length is ${bytes.size}")

        println("done")
    }

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
        val schemaServer = KafkaSchemaRegistryApi(KafkaSchemaRegistryConfig("http://localhost:8082", 60.seconds), CIO.create(), health, running)

        val reply = schemaServer.checkConnection()

        println("created $reply")
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
