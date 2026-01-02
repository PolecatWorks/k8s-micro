package com.polecatworks.kotlin.k8smicro

import com.polecatworks.kotlin.k8smicro.eventSerde.Event
import com.polecatworks.kotlin.k8smicro.eventSerde.EventSchemaManager
import com.polecatworks.kotlin.k8smicro.eventSerde.EventSerde
import com.polecatworks.kotlin.k8smicro.health.HealthSystem
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.Serdes.Long
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StoreQueryParameters
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Grouped
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.kstream.Printed
import org.apache.kafka.streams.kstream.Produced
import org.apache.kafka.streams.state.KeyValueIterator
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

data class KafkaProcessorConfig constructor(
    val hostUrl: String,
    val schemaRegistry: KafkaSchemaRegistryConfig,
    val readTopic: String,
    val writeTopic: String,
    val applicationId: String = "kotlin1",
    val bootstrapServers: String = "localhost:9092",
    val processingGuarantee: String = StreamsConfig.EXACTLY_ONCE_V2,
    val autoOffsetReset: String,
    val taskSleepDuration: Duration = 10.seconds,
    val querySleep: Duration = 15.seconds,
)

class KafkaProcessor(
    val config: KafkaProcessorConfig,
    val health: HealthSystem,
    val engine: HttpClientEngine,
    val running: AtomicBoolean,
    val applicationServer: String,
) {
    val schemaRegistryApi = KafkaSchemaRegistryApi(config.schemaRegistry, engine, health, running)

//    val chaserSchema = Avro.schema<Chaser>()
    var chaserId: Int? = null

    private val aggregateStoreName = "aggregate-store"
    private var streamsInstance: KafkaStreams? = null

    suspend fun start() =
        coroutineScope {
            logger.info("Starting kafka processing")
            launch { schemaRegistryApi.start() }

//            println("Checking for schema: $chaserSchema")

            // TODO: Should be a liveness check associated with this loop
//            while (running.get() && chaserId === null) {
//                try {
//                    chaserId = schemaRegistryApi.registerSchema("${config.readTopic}-value", chaserSchema.toString())
//                    println("Got a chaser = $chaserId")
//                } catch (e: Exception) {
//                    println("I got the error : $e so sleeping for ${config.querySleep}")
//                    delay(config.querySleep)
//                }
//            }

//        if (true) {
//            val myChaser = Chaser("ABCNAME", "ID1", 12, 22, null)
//            val baos = ByteArrayOutputStream()
//            Avro.default.openOutputStream(Chaser.serializer()) {
//                encodeFormat = AvroEncodeFormat.Binary
//            }.to(baos).write(myChaser).close()
//
//            val baosString = baos.toString()
//            logger.info("Looking at output = $baosString of length ${baosString.length}")
//
//            val ben = Avro.default.toRecord(Chaser.serializer(), myChaser)
//            logger.info("Chaser = $ben")
//
//            logger.info("Chaser Schema registered for topic ${config.readTopic} with id: $chaserId")
//        }
//        if (true) {
//            val serializer = KafkaAvro4kSerializer()
//            serializer.configure(
//                mapOf(
//                    AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS to "true",
//                    AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to schemaRegistryApi.schemaRegistryUrl
//                ),
//                false
//            )
//            val avroSchema = Avro.default.schema(Chaser.serializer())
//            val topic = "input"
//            val subjectName = "$topic-value"
//
//            val myChaser = Chaser("ABCNAME", "ID1", 12, 22)
//            val result = serializer.serialize(topic, myChaser)
//
//            logger.info("Serdes using xxxx = $result")
//        }

            val streamsBuilder = StreamsBuilder()
            val streamProperties =
                mapOf<String, String>(
                    StreamsConfig.APPLICATION_ID_CONFIG to config.applicationId,
                    StreamsConfig.BOOTSTRAP_SERVERS_CONFIG to config.bootstrapServers,
                    StreamsConfig.PROCESSING_GUARANTEE_CONFIG to config.processingGuarantee,
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to config.autoOffsetReset,
                    StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG to Serdes.String()::class.java.name,
                    AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to schemaRegistryApi.schemaRegistryUrl,
                    StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG to
                        org.apache.kafka.streams.errors.LogAndContinueExceptionHandler::class.java.name,
                    StreamsConfig.APPLICATION_SERVER_CONFIG to applicationServer,
                ).toProperties()

            if (true) {
                val eventSchemaManager = EventSchemaManager(schemaRegistryApi)
                eventSchemaManager.registerAllSchemas(config.readTopic)
                val eventSerde = EventSerde()

                eventSerde.setSchemaManager(eventSchemaManager)

                val mystream1Specific = streamsBuilder.stream<String, Event>(config.readTopic, Consumed.with(Serdes.String(), eventSerde))

                mystream1Specific
                    .print(Printed.toSysOut<String, Event>().withLabel("Avro4k"))

                val mycountSpecific =
                    mystream1Specific
                        .map { k, v ->
                            when (v) {
                                is Event.Pizza -> KeyValue("$k-pizza", 1L)
                                is Event.Burger -> KeyValue("$k-burger", 1L)
                                is Event.Chaser -> KeyValue("$k-${v.name}", 1L)
                                is Event.Aggregate -> KeyValue("$k-Agg", 1L)
                            }
                        }.groupByKey(Grouped.with(Serdes.String(), Long()))
                        .reduce { aggValue, newValue -> aggValue!! + newValue!! }
                        .toStream()

                mycountSpecific
                    .print(Printed.toSysOut<String?, Long?>().withLabel("Specific-count"))

                val myAggregate1 =
                    mystream1Specific
                        .groupByKey()
                        .aggregate<Event>(
                            { Event.Aggregate(listOf(), 1, null, null) as Event },
                            { k: String, v: Event, agg: Event ->
                                val value =
                                    when (v) {
                                        is Event.Aggregate -> v
                                        is Event.Chaser -> Event.Aggregate(listOf(v.name), 1, v.sent, v.ttl)
                                        else -> Event.Aggregate(listOf(v.javaClass.name), 0, null, null)
                                    }
                                val aggregate =
                                    when (agg) {
                                        is Event.Aggregate -> agg
                                        else -> throw IllegalArgumentException("Agg should not be class ${agg.javaClass.name}.")
                                    }
                                val uniqueNames = (value.names + aggregate.names).distinct()
                                val count = value.count + aggregate.count
                                val latest = maxOf(value.latest ?: 0, aggregate.latest ?: 0)
                                val longest = maxOf(value.longest ?: 0, aggregate.longest ?: 0)

                                Event.Aggregate(uniqueNames, count, latest, longest) as Event
                            },
                            Materialized
                                .`as`<String, Event, KeyValueStore<Bytes, ByteArray>>(aggregateStoreName)
                                .withKeySerde(Serdes.String())
                                .withValueSerde(eventSerde),
                        ).toStream(Named.`as`("aggout-merger"))

                myAggregate1
                    .print(Printed.toSysOut<String?, Event?>().withLabel("Specific-agg"))

                myAggregate1
                    .to(config.writeTopic, Produced.with(Serdes.String(), eventSerde))

//                        .toStream( Produced.with(Serdes.String(), eventSerde), Named.`as`("aggmout-merger"))
//                myAggregate1
//                    .print(Printed.toSysOut<String?, Event.Aggregate?>().withLabel("Specific-agg"))
            }

            if (false) {
                // Setup with Confluent GenericRecord
                val genericAvroSerde: Serde<GenericRecord> = GenericAvroSerde()
                genericAvroSerde.configure(
                    mapOf(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to schemaRegistryApi.schemaRegistryUrl),
                    false,
                )

                val streamGeneric =
                    streamsBuilder
                        .stream(config.readTopic, Consumed.with(Serdes.String(), genericAvroSerde))
                streamGeneric
                    .print(Printed.toSysOut<String?, GenericRecord?>().withLabel("Generic"))

                val counts1Generic =
                    streamGeneric
                        .map { k, v ->
                            val name = v.get("name")
                            KeyValue("$k-$name", 1L)
                        }.groupByKey(Grouped.with(Serdes.String(), Long()).withName("generic-group"))
                        .reduce({ aggValue, newValue -> aggValue!! + newValue!! }, Materialized.`as`("generic-table"))
                        .toStream()
                counts1Generic
                    .print(Printed.toSysOut<String?, Long?>().withLabel("Generic-count"))
            }

            val topology = streamsBuilder.build()

            print(topology.describe())

            val streams = KafkaStreams(topology, streamProperties)

            streams.setUncaughtExceptionHandler { e ->
                logger.error(e) { "Streams crashed" }
                running.set(false)
                StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_APPLICATION
            }

            streams.setStateListener { newState, oldState ->
                logger.info { "Streams state $oldState -> $newState" }
            }

            logger.info("Reading from input topic ${config.readTopic}")

            streams.start()
            streamsInstance = streams

//            .stream(config.readTopic, Consumed.with(Serdes.String(), Serdes.String()))
            // .print(Printed.toSysOut<String, String>().withLabel("input-stream"))
            // See here for custom serdes: https://github.com/confluentinc/examples/blob/7.3.2-post/clients/cloud/kotlin/src/main/kotlin/io/confluent/examples/clients/cloud/StreamsExample.kt

//        val counts = records.map { k, v ->
//            println("OKLY DOKLEY $k, $v")
//
//
//
// //            val result = deserializer.deserialize("input-value", v) as Chaser
// //            println("The output value is ${result.name}")
//            val name=v.name
// //            val name = result.name
//
// //            val name = v.get("name").toString()
// //            println("Got id = $name")
// //            AvroSerializer<Chaser>.decodeAvroValue()
//
//            KeyValue(name, 1L)
// //            KeyValue(k, v.length.toLong())
//        }
// //        counts.print(Printed.toSysOut<String, Long>().withLabel("Consumed record"))
//        val countAgg = counts
//            .groupByKey(Grouped.with(Serdes.String(), Long()))
//            .reduce { aggValue, newValue -> aggValue!! + newValue!! }
//            .toStream()
//        countAgg.print(Printed.toSysOut<String, Long>().withLabel("Running count"))

//
// //        var streams: KafkaStreams? = null
//        val kthread = thread {
//            streams = KafkaStreams(builder.build(), streamProperties)
// //                .use {
// //                it.start()
// // //                println("Starting use")
// //            }
//            logger.info("Constructed Kafka streams")
//            streams!!.start()
//            logger.info("Started Kafka streams")
//        }

            logger.info("Kafka streams started")

            try {
                while (running.get()) {
                    println("**** Kafka task sleep waiting for it to complete")
                    delay(config.taskSleepDuration)
                }
            } finally {
                streams.close(java.time.Duration.ofSeconds(10))
                streamsInstance = null
                logger.info("Ended kafka task")
            }
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

    fun writeSchemaId(
        out: ByteArrayOutputStream,
        id: Int,
    ) {
        out.write(0)
        out.write(ByteBuffer.allocate(4).putInt(id).array())
    }

    init {
        println("Initialising Kafka Processor: $config")
    }

    fun getAggregate(key: String): Event? {
        val localStreams = streamsInstance ?: return null
        return try {
            val store: ReadOnlyKeyValueStore<String, Event> =
                localStreams.store(
                    StoreQueryParameters.fromNameAndType(
                        aggregateStoreName,
                        QueryableStoreTypes.keyValueStore<String, Event>(),
                    ),
                )
            store.get(key)
        } catch (e: Exception) {
            logger.warn(e) { "Aggregate store query failed for key=$key" }
            null
        }
    }

    fun getAllAggregateKeys(): List<String> {
        val localStreams = streamsInstance ?: return emptyList()
        return try {
            val store: ReadOnlyKeyValueStore<String, Event> =
                localStreams.store(
                    StoreQueryParameters.fromNameAndType(
                        aggregateStoreName,
                        QueryableStoreTypes.keyValueStore<String, Event>(),
                    ),
                )
            val keys = mutableListOf<String>()
            val iterator: KeyValueIterator<String, Event> = store.all()
            iterator.use {
                while (it.hasNext()) {
                    val kv = it.next()
                    keys.add(kv.key)
                }
            }
            keys
        } catch (e: Exception) {
            logger.warn(e) { "Aggregate store keys query failed" }
            emptyList()
        }
    }

    fun getStoreMetaData(key: String): org.apache.kafka.streams.KeyQueryMetadata? {
        val localStreams = streamsInstance ?: return null
        return try {
            localStreams.queryMetadataForKey(aggregateStoreName, key, Serdes.String().serializer())
        } catch (e: Exception) {
            logger.warn(e) { "Metadata query failed for key=$key" }
            null
        }
    }
}
