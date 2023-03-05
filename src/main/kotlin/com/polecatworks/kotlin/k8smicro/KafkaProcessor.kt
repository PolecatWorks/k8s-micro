package com.polecatworks.kotlin.k8smicro

import com.github.avrokotlin.avro4k.Avro
import com.github.avrokotlin.avro4k.AvroNamespace
import com.polecatworks.kotlin.k8smicro.health.HealthSystem
import io.ktor.client.engine.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

data class KafkaProcessorConfig constructor(
    val hostUrl: String,
    val schemaRegistry: KafkaSchemaRegistryConfig,
    val readTopic: String,
    val writeTopic: String,
    val taskSleepDuration: Duration = 10.seconds,
    val querySleep: Duration = 15.seconds
)

/*
Copied from rust
pub(crate) struct Chaser {
    pub(crate) name: String,
    pub(crate) id: String,
    pub(crate) sent: i64,
    pub(crate) ttl: u32,
    pub(crate) previous: Option<i64>,
}
 */
@AvroNamespace("com.polecatworks.chaser")
@Serializable
data class Chaser(
    val name: String,
    val id: String,
    val send: Long,
    val ttl: Int,
    val previous: Long?
)

class KafkaProcessor(
    val config: KafkaProcessorConfig,
    val health: HealthSystem,
    val engine: HttpClientEngine,
    val running: AtomicBoolean
) {

    val schemaRegistryApi = KafkaSchemaRegistryApi(config.schemaRegistry, engine, health, running)
    val chaserSchema = Avro.default.schema(Chaser.serializer())
    var chaserId: Int? = null
    suspend fun start() = coroutineScope {
        logger.info("Starting kafka processing")
        launch { schemaRegistryApi.start() }

        println("Checking for schema: $chaserSchema")

        while (running.get() && chaserId === null) {
            try {
                chaserId = schemaRegistryApi.registerSchema("${config.readTopic}-value", chaserSchema.toString())
            } catch (e: Exception) {
                println("I got the error : $e so sleeping for ${config.querySleep}")
                delay(config.querySleep)
            }
        }
        logger.info("Chaser Schema registered for topic ${config.readTopic} with id: $chaserId")
        while (running.get()) {
            println("**** Kafka task")
            delay(config.taskSleepDuration)
        }
        logger.info("Ended kafka task")
    }

    init {
        println("Initialising Kafka Processor: $config")
    }
}
