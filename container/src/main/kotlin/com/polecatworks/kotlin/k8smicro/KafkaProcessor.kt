package com.polecatworks.kotlin.k8smicro

import com.polecatworks.kotlin.k8smicro.eventSerde.Event
import com.polecatworks.kotlin.k8smicro.eventSerde.EventSchemaManager
import com.polecatworks.kotlin.k8smicro.eventSerde.EventSerde
import com.polecatworks.kotlin.k8smicro.health.HealthSystem
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.ktor.client.engine.HttpClientEngine
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerConfig
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
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

data class KafkaProcessorConfig constructor(
    val hostUrl: String,
    val schemaRegistry: KafkaSchemaRegistryConfig,
    val readTopic: String,
    val writeTopic: String,
    val billingOutputTopic: String,
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
    private val metricsRegistry: PrometheusMeterRegistry,
) {
    val schemaRegistryApi = KafkaSchemaRegistryApi(config.schemaRegistry, engine, health, running)

    var chaserId: Int? = null

    private val chaserStoreName = "chaser"
    private val billingStoreName = "billing"

    private var streamsInstance: KafkaStreams? = null

    suspend fun start() =
        coroutineScope {
            logger.info("Starting kafka processing")
            launch { schemaRegistryApi.start() }

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

            val eventSerde = EventSerde()
            val eventSchemaManager = EventSchemaManager(schemaRegistryApi)
            eventSchemaManager.registerAllSchemas(config.readTopic)
            eventSerde.setSchemaManager(eventSchemaManager)

            val topology = buildTopology(streamsBuilder, eventSerde)

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

            logger.info("Kafka streams started")

            try {
                while (running.get()) {
                    logger.debug("**** Kafka task sleep waiting for it to complete")
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

    fun getChaserAggregate(key: String): Event? {
        val localStreams = streamsInstance ?: return null
        return try {
            val store: ReadOnlyKeyValueStore<String, Event> =
                localStreams.store(
                    StoreQueryParameters.fromNameAndType(
                        chaserStoreName,
                        QueryableStoreTypes.keyValueStore<String, Event>(),
                    ),
                )
            store.get(key)
        } catch (e: Exception) {
            logger.warn(e) { "Aggregate store query failed for key=$key" }
            null
        }
    }

    fun getAllChaserAggregateKeys(): List<String> {
        val localStreams =
            streamsInstance ?: run {
                logger.error("streamsInstance is null")
                return emptyList()
            }

        logger.info("Got localStreams. Checking for all keys")

        return try {
            val store: ReadOnlyKeyValueStore<String, Event> =
                localStreams.store(
                    StoreQueryParameters.fromNameAndType(
                        chaserStoreName,
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

    fun getChaserStoreMetaData(key: String): org.apache.kafka.streams.KeyQueryMetadata? {
        val localStreams = streamsInstance ?: return null
        return try {
            localStreams.queryMetadataForKey(chaserStoreName, key, Serdes.String().serializer())
        } catch (e: Exception) {
            logger.warn(e) { "Metadata query failed for key=$key" }
            null
        }
    }

    fun getBillingAggregate(key: String): Event? {
        val localStreams = streamsInstance ?: return null
        return try {
            val store: ReadOnlyKeyValueStore<String, Event> =
                localStreams.store(
                    StoreQueryParameters.fromNameAndType(
                        billingStoreName,
                        QueryableStoreTypes.keyValueStore<String, Event>(),
                    ),
                )
            store.get(key)
        } catch (e: Exception) {
            logger.warn(e) { "Billing store query failed for key=$key" }
            null
        }
    }

    fun getAllBillingAggregateKeys(): List<String> {
        val localStreams =
            streamsInstance ?: run {
                logger.error("streamsInstance is null")
                return emptyList()
            }

        return try {
            val store: ReadOnlyKeyValueStore<String, Event> =
                localStreams.store(
                    StoreQueryParameters.fromNameAndType(
                        billingStoreName,
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
            logger.warn(e) { "Billing store keys query failed" }
            emptyList()
        }
    }

    fun getBillingStoreMetaData(key: String): org.apache.kafka.streams.KeyQueryMetadata? {
        val localStreams = streamsInstance ?: return null
        return try {
            localStreams.queryMetadataForKey(billingStoreName, key, Serdes.String().serializer())
        } catch (e: Exception) {
            logger.warn(e) { "Billing metadata query failed for key=$key" }
            null
        }
    }

    fun buildTopology(
        streamsBuilder: StreamsBuilder,
        eventSerde: EventSerde,
    ): org.apache.kafka.streams.Topology {
        val chaserTimer =
            metricsRegistry
                .timer("chaser.received")

        val chaserTtlGauge =
            metricsRegistry
                .gauge("chaser.ttl.value", AtomicLong(0))!!

        val billingAggregateTimer =
            metricsRegistry
                .timer("billing.aggregation")
        val billingBillsTimer =
            metricsRegistry
                .timer("billing.customer.bills")

        val mystream1Specific = streamsBuilder.stream<String, Event>(config.readTopic, Consumed.with(Serdes.String(), eventSerde))

        mystream1Specific
            .print(Printed.toSysOut<String, Event>().withLabel("Avro4k"))

        val mycountSpecific =
            mystream1Specific
                .filter { k, v -> v is Event.Chaser || v is Event.Bill || v is Event.PaymentRequest || v is Event.PaymentFailed }
                .map { k, v ->
                    when (v) {
                        is Event.Chaser -> KeyValue(v.name, 1L) // Count of chasers by name
                        is Event.Bill -> KeyValue("Bill-$k", 1L) // Count of Bills for a given transations
                        is Event.PaymentRequest -> KeyValue("PayReq-$k", 1L) // Count of payment requests for a given transaction
                        is Event.PaymentFailed -> KeyValue("PayFail-$k", 1L) // Count of payment failures for a given transaction
                        else -> throw IllegalArgumentException("Unknown event type: ${v.javaClass.name} should have been filtered out")
                    }
                }.groupByKey(Grouped.with(Serdes.String(), Long()))
                .reduce { aggValue, newValue -> aggValue!! + newValue!! }
                .toStream()

        mycountSpecific
            .print(Printed.toSysOut<String?, Long?>().withLabel("Specific-count"))

        val chaserStream =
            mystream1Specific
                .filter { k, v -> v is Event.Chaser }

        val chaserAggregate =
            chaserStream
                .groupByKey()
                .aggregate<Event>(
                    { Event.Aggregate(listOf(), 1, null, null) as Event },
                    { k: String, v: Event, agg: Event ->
                        if (v is Event.Chaser) {
                            chaserTimer.record(java.time.Duration.ZERO)
                            chaserTtlGauge.set(v.ttl)
                        }
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
                        .`as`<String, Event, KeyValueStore<Bytes, ByteArray>>(chaserStoreName)
                        .withKeySerde(Serdes.String())
                        .withValueSerde(eventSerde),
                ).toStream(Named.`as`("aggout-merger"))

        chaserAggregate
            .print(Printed.toSysOut<String?, Event?>().withLabel("Specific-agg"))

        chaserAggregate
            .to(config.writeTopic, Produced.with(Serdes.String(), eventSerde))

        val billingStream =
            mystream1Specific
                .filter { k, v -> v is Event.Bill || v is Event.PaymentRequest || v is Event.PaymentFailed }

        val billingAggregate =
            billingStream
                .groupByKey()
                .aggregate<Event>(
                    { Event.BillAggregate() },
                    { k: String, v: Event, agg: Event ->
                        val currentAgg = agg as? Event.BillAggregate ?: Event.BillAggregate()

                        val customerId =
                            when (v) {
                                is Event.Bill -> v.customerId
                                is Event.PaymentRequest -> v.customerId
                                is Event.PaymentFailed -> v.customerId
                                else -> "unknown"
                            }

                        billingAggregateTimer.record(java.time.Duration.ZERO)

                        // Specifically track number of distinct bills per customer
                        if (v is Event.Bill && currentAgg.bill == null) {
                            billingBillsTimer.record(java.time.Duration.ZERO)
                        }

                        when (v) {
                            is Event.BillAggregate -> v
                            is Event.Bill -> {
                                val isErrored = currentAgg.bill != null && currentAgg.bill != v
                                currentAgg.copy(bill = v, errored = currentAgg.errored || isErrored)
                            }
                            is Event.PaymentRequest -> {
                                val requests =
                                    if (currentAgg.paymentRequests.any { it.paymentId == v.paymentId }) {
                                        currentAgg.paymentRequests
                                    } else {
                                        currentAgg.paymentRequests + v
                                    }
                                currentAgg.copy(paymentRequests = requests, lastPaymentFailed = null)
                            }
                            is Event.PaymentFailed -> {
                                if (currentAgg.lastPaymentFailed != null) {
                                    currentAgg.copy(errored = true)
                                } else {
                                    currentAgg.copy(lastPaymentFailed = v)
                                }
                            }
                            else -> agg
                        }
                    },
                    Materialized
                        .`as`<String, Event, KeyValueStore<Bytes, ByteArray>>(billingStoreName)
                        .withKeySerde(Serdes.String())
                        .withValueSerde(eventSerde),
                ).toStream(Named.`as`("billing-merger"))

        billingAggregate
            .print(Printed.toSysOut<String?, Event?>().withLabel("Specific-agg"))

        billingAggregate
            .to(config.billingOutputTopic, Produced.with(Serdes.String(), eventSerde))

        return streamsBuilder.build()
    }
}
