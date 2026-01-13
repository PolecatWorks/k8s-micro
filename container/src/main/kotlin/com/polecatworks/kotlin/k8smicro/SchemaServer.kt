package com.polecatworks.kotlin.k8smicro
import com.polecatworks.kotlin.k8smicro.health.AliveMarginCheck
import com.polecatworks.kotlin.k8smicro.health.HealthSystem
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
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
    val threadSleep: Duration = 5.seconds,
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
    val running: AtomicBoolean,
) {
    @Serializable
    data class SchemaRegisterReferences(
        val name: String,
        val subject: String,
        val version: Int,
    )

    @Serializable
    data class SchemaRegister(
        val schema: String,
        val schemaType: String,
        val references: List<SchemaRegisterReferences> = listOf(),
    )

    @Serializable
    data class SchemaIdReply(
        val id: Int,
    )

    @Serializable
    data class SchemaErrorReply(
        val error_code: Int,
        val message: String,
    )

    private val httpClient =
        HttpClient(engine) {
            // install(Logging)
            install(ContentNegotiation) {
                json(
                    kotlinx.serialization.json.Json {
                        ignoreUnknownKeys = true
                    },
                )
            }
        }

    val schemaRegistryUrl = config.hostUrl

    @OptIn(ExperimentalTime::class)
    suspend fun start() =
        coroutineScope {
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
        val reply =
            try {
                httpClient.get("$schemaRegistryUrl/config")
            } catch (error: ConnectException) {
                println("Error reply from SchemaRegistry: $error")
                return false
            }
        return reply.status == HttpStatusCode.OK
    }

    suspend fun registerSchema(
        subject: String,
        schema: String,
    ): Int {
        logger.info("Registering schema for $subject with $schema")
        val response =
            httpClient.post {
                url("$schemaRegistryUrl/subjects/$subject/versions")
                contentType(ContentType.Application.Json)
                setBody(SchemaRegister(schema, "AVRO"))
            }

        if (response.status !in listOf(HttpStatusCode.OK, HttpStatusCode.Created)) {
            val error: SchemaErrorReply = response.body()
            throw IllegalStateException("Failed to register schema for $subject: ${error.message} (error_code: ${error.error_code})")
        }

        val reply: SchemaIdReply = response.body()

        return reply.id
    }

    suspend fun getLatestSchema(subject: String): String =
        httpClient
            .get {
                url("$schemaRegistryUrl/subjects/$subject/versions/latest/schema")
            }.body()

    suspend fun getSchemaByVersion(
        subject: String,
        version: Int,
    ): String =
        httpClient
            .get {
                url("$schemaRegistryUrl/subjects/$subject/versions/$version/schema")
            }.body()

    suspend fun checkCompatibility(
        subject: String,
        schema: String,
    ): Boolean {
        @Serializable
        data class CompatibilityReply(
            val is_compatible: Boolean,
        )

        val response =
            httpClient.post {
                url("$schemaRegistryUrl/compatibility/subjects/$subject/versions/latest")
                contentType(ContentType.Application.Json)
                setBody(SchemaRegister(schema, "AVRO"))
            }
        return response.body<CompatibilityReply>().is_compatible
    }

    suspend fun updateCompatibility(
        subject: String,
        compatibilityLevel: String,
    ): String =
        httpClient
            .put {
                url("$schemaRegistryUrl/config/$subject")
                contentType(ContentType.Application.Json)
                setBody("{\"compatibility\": \"$compatibilityLevel\"}")
            }.body()
}
