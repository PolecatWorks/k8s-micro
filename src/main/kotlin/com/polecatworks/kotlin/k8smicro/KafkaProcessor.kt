package com.polecatworks.kotlin.k8smicro

import com.github.avrokotlin.avro4k.Avro
import com.github.avrokotlin.avro4k.AvroNamespace
import com.github.avrokotlin.avro4k.io.AvroEncodeFormat
import com.polecatworks.kotlin.k8smicro.health.HealthSystem
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde
import io.ktor.client.engine.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.Serdes.Long
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Grouped
import org.apache.kafka.streams.kstream.Printed
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
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
    val sent: Long,
    val ttl: Long,
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
        val myChaser = Chaser("ABCNAME", "ID1", 12, 22, previous = null)
//        val abc = AvroSerializer.serialize(encoder, myChaser)
        val baos = ByteArrayOutputStream()
        Avro.default.openOutputStream(Chaser.serializer()) {
            encodeFormat = AvroEncodeFormat.Binary
        }.to(baos).write(myChaser).close()

        val baosString = baos.toString()
        logger.info("Looking at output = $baosString of length ${baosString.length}")

//        val ben2 = Chaser.serializer()
//        logger.info("Serializer = $ben2")
        val ben = Avro.default.toRecord(Chaser.serializer(), myChaser)
//        val ben2 = ben.
        logger.info("Chaser = $ben")

        logger.info("Chaser Schema registered for topic ${config.readTopic} with id: $chaserId")

        val builder = StreamsBuilder()

        logger.info("Reading from input topic ${config.readTopic}")

        val genericAvroSerde: Serde<GenericRecord> = GenericAvroSerde()
        genericAvroSerde.configure(
            mapOf(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to schemaRegistryApi.schemaRegistryUrl),
            /*isKey*/ false
        )

        val records = builder
            .stream(config.readTopic, Consumed.with(Serdes.String(), genericAvroSerde))
        // .print(Printed.toSysOut<String, String>().withLabel("input-stream"))
        // See here for custom serdes: https://github.com/confluentinc/examples/blob/7.3.2-post/clients/cloud/kotlin/src/main/kotlin/io/confluent/examples/clients/cloud/StreamsExample.kt

        val counts = records.map { k, v ->
//            println("OKLY DOKLEY $k, $v")
            val name = v.get("name").toString()
//            println("Got id = $name")
//            AvroSerializer<Chaser>.decodeAvroValue()

            KeyValue(name, 1L)
//            KeyValue(k, v.length.toLong())
        }
//        counts.print(Printed.toSysOut<String, Long>().withLabel("Consumed record"))
        val countAgg = counts
            .groupByKey(Grouped.with(Serdes.String(), Long()))
            .reduce { aggValue, newValue -> aggValue!! + newValue!! }
            .toStream()
        countAgg.print(Printed.toSysOut<String, Long>().withLabel("Running count"))

        val streamProperties = mapOf<String, String>(
            StreamsConfig.APPLICATION_ID_CONFIG to "kotlin1",
            StreamsConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092"
        ).toProperties()

        var streams: KafkaStreams? = null
        val kthread = thread {
            streams = KafkaStreams(builder.build(), streamProperties)
//                .use {
//                it.start()
// //                println("Starting use")
//            }
            logger.info("Constructed Kafka streams")
            streams!!.start()
            logger.info("Started Kafka streams")
        }

        logger.info("Kafka streams started in a a thread")

        while (running.get()) {
            println("**** Kafka task sleep waiting for it to complete")
            delay(config.taskSleepDuration)
        }

        streams!!.close()
        kthread.join()
        logger.info("Ended kafka task")
    }

    /* Look to use a proper kafka serdes for this process so we can apply that directly to the kafka values.
    Then we can consider to use the kafka streams API for processing the data.
     */
    fun writeMe(obj: Any?): ByteArray {
        val out = ByteArrayOutputStream()
        writeSchemaId(out, chaserId!!)
//        if (obj is ByteArray) {
//            out.write(obj)
//        } else {
//            serializeValue(out, obj, currentSchema)
//        }
        val bytes = out.toByteArray()
        out.close()
        return bytes
    }

    fun writeSchemaId(out: ByteArrayOutputStream, id: Int) {
        out.write(0)
        out.write(ByteBuffer.allocate(4).putInt(id).array())
    }

    init {
        println("Initialising Kafka Processor: $config")
    }
}
