package com.polecatworks.kotlin.k8smicro
import com.polecatworks.kotlin.k8smicro.health.AliveMarginCheck
import com.polecatworks.kotlin.k8smicro.health.HealthSystem
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import java.net.ConnectException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

private val logger = KotlinLogging.logger {}

data class KafkaSchemaRegistryConfig(
    val hostUrl: String,
    val checkDuration: Duration,
    val threadSleep: Duration = 5.seconds
//    val checkMargin: Duration = 1.seconds,
) {
//    init {
//        check(checkTime>checkMargin) { "checkTime must be greater than checkMargin" }
//    }
}

class KafkaSchemaRegistryApi(
    val config: KafkaSchemaRegistryConfig,
    val engine: HttpClientEngine,
    val health: HealthSystem,
    val running: AtomicBoolean
) {

    @Serializable
    data class SchemaRegisterReferences(
        val name: String,
        val subject: String,
        val version: Int
    )

    @Serializable
    data class SchemaRegister(
        val schema: String,
        val schemaType: String,
        val references: List<SchemaRegisterReferences> = listOf()
    )

    @Serializable
    data class SchemaIdReply(
        val id: Int
    )

    private val httpClient = HttpClient(engine) {
        // install(Logging)
        install(ContentNegotiation) {
            json()
        }
    }

    private val schemaRegistryUrl = config.hostUrl

    @OptIn(ExperimentalTime::class)
    suspend fun start() = coroutineScope {
        logger.info("Starting alive check on Schema Registry")
        val myAlive = AliveMarginCheck("SchemaRegistry", config.checkDuration, false)
        launch {
            health.registerAlive(myAlive)
            val schemaRegistryApi = KafkaSchemaRegistryApi(config, engine, health, running)
            var checkinTime = TimeSource.Monotonic.markNow()
            while (running.get()) {
                val timeNow = TimeSource.Monotonic.markNow()
                if (checkinTime < timeNow) {
                    if (schemaRegistryApi.checkConnection()) {
                        logger.info("Confirmed connection to Kafka Schema Registry")
                        myAlive.kick()
                        checkinTime = timeNow + config.checkDuration
                    } else {
                        logger.warn("Cannot connect to Kafka Schema Registry")
                    }
                }
                delay(config.threadSleep)
            }
            health.deregisterAlive(myAlive)
            running.set(false)
            logger.info("Stopped alive check on Schema Registry")
        }
    }

    suspend fun checkConnection(): Boolean {
        val reply = try {
            httpClient.get("$schemaRegistryUrl/config")
        } catch (error: ConnectException) {
            println("Error reply from SchemaRegistry: $error")
            return false
        }
        return reply.status == HttpStatusCode.OK
    }

    suspend fun registerSchema(subject: String, schema: String): Int {
        logger.info("Registering schema for $subject with $schema")
        val response = httpClient.post {
            url("$schemaRegistryUrl/subjects/$subject/versions")
            contentType(ContentType.Application.Json)
            setBody(SchemaRegister(schema, "AVRO"))
        }
        val reply: SchemaIdReply = response.body()

        return reply.id
    }

    suspend fun getLatestSchema(subject: String): String {
        return httpClient.get {
            url("$schemaRegistryUrl/$subject/versions/latest/schema")
        }.toString()
    }

    suspend fun getSchemaByVersion(subject: String, version: Int): String {
        return httpClient.get {
            url("$schemaRegistryUrl/$subject/versions/$version/schema")
        }.body()
    }

    suspend fun checkCompatibility(subject: String, schema: String): Boolean {
        val response = httpClient.post {
            url("$schemaRegistryUrl/$subject/versions/latest")
            setBody(schema)
        }
        return response.body()
    }

    suspend fun updateCompatibility(subject: String, compatibilityLevel: String): String {
        return httpClient.put {
            url("$schemaRegistryUrl/$subject/config")
            setBody("{\"compatibility\": \"$compatibilityLevel\"}")
        }.body()
    }
}
