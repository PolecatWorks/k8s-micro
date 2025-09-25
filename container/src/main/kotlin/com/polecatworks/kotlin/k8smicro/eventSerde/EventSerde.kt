package com.polecatworks.kotlin.k8smicro.eventSerde

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.avrokotlin.avro4k.Avro
import kotlinx.serialization.encodeToByteArray
import org.apache.kafka.common.Configurable
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serializer
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class EventSerde<T : Event> :
    Serializer<T?>,
    Deserializer<T?>,
    Serde<T?>,
    Configurable {
    companion object {
        const val MAGIC_BYTE: Byte = 0x0
        const val SCHEMA_ID_SIZE = 4
        private val objectMapper = ObjectMapper()
        private var schemaManager: EventSchemaManager? = null
    }

    fun setSchemaManager(mySchemaManager: EventSchemaManager) {
        schemaManager = mySchemaManager
    }

    override fun configure(p0: Map<String?, *>?) {
        TODO("Not yet implemented")
    }

    override fun configure(
        configs: Map<String?, *>?,
        isKey: Boolean,
    ) {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    override fun serializer(): Serializer<T?> = this

    override fun deserializer(): Deserializer<T?>? = this

    override fun serialize(
        topic: String?,
        data: T?,
    ): ByteArray? {
        if (topic == null) return null
        if (data == null) return null

        try {
            val outputStream = ByteArrayOutputStream()
            outputStream.write(MAGIC_BYTE.toInt())

            val schemaId = schemaManager!!.getSchemaIdForEvent(data)!!

            outputStream.write(ByteBuffer.allocate(EventSerializer.Companion.SCHEMA_ID_SIZE).putInt(schemaId).array())

            val outputBytes =
                when (data) {
                    is Event.Burger -> Avro.encodeToByteArray<Event.Burger>(data)
                    is Event.Pizza -> Avro.encodeToByteArray<Event.Pizza>(data)
                    is Event.Chaser -> Avro.encodeToByteArray<Event.Chaser>(data)
                    is Event.Aggregate -> Avro.encodeToByteArray<Event.Aggregate>(data)
                    // When is exhaustive so compiler will fault if we do not complete all options
                }

            outputStream.write(outputBytes)

            return outputStream.toByteArray()
        } catch (e: Exception) {
            throw SerializationException("Serialisation error: $e")
        }
    }

    override fun deserialize(
        topic: String?,
        data: ByteArray?,
    ): T? {
        if (data == null) return null
        if (topic == null) return null

        try {
            if (data.size < EventDeSerializer.Companion.SCHEMA_ID_SIZE + 1 + 1) throw IllegalArgumentException("Data block is too short")

            val magicByte = data[0]

            if (magicByte != EventDeSerializer.Companion.MAGIC_BYTE) throw IllegalArgumentException("Invalid magic byte: $magicByte")

            val schemaId = ByteBuffer.wrap(data.sliceArray(1..EventDeSerializer.Companion.SCHEMA_ID_SIZE)).getInt()

            val deserializer =
                schemaManager!!.getDeserializerForSchemaId(schemaId)
                    ?: throw SerializationException("No deserializer found for $schemaId")

            val objBytes = data.sliceArray(EventDeSerializer.Companion.SCHEMA_ID_SIZE + 1..<data.size)

            return Avro.decodeFromByteArray(deserializer, objBytes) as T
        } catch (e: Exception) {
            throw SerializationException("Deserialisation error: $e")
        }
    }
}

//
//    fun abd(
// }
//    private val eventSchemaManager: EventSchemaManager,
// ) : Serde<T> {
//    private val avro4kSerializerb = EventSerializer(eventSchemaManager)
//    private val avro4kDeserializerb = EventDeSerializer(eventSchemaManager)
//
//    private val avro4kSerializer =
//        Serializer<T> { subject, data ->
//            avro4kSerializerb.serialize(subject, data)
//        }
//
//    private val avro4kDeserializer =
//        Deserializer<T> { subject, data: ByteArray? ->
//            avro4kDeserializerb.deserialize(subject, data) as T?
//        }
//
//    override fun serializer(): Serializer<T> = avro4kSerializer
//
//    override fun deserializer(): Deserializer<T> = avro4kDeserializer
// }
//
//
// object PageViewTypedDemo {
//    @JvmStatic
//    fun main(args: Array<String>) {
//        val props = Properties()
//        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "streams-pageview-typed")
//        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
//        props.put(StreamsConfig.DEFAULT_TIMESTAMP_EXTRACTOR_CLASS_CONFIG, JsonTimestampExtractor::class.java)
//        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, JSONSerde::class.java)
//        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, JSONSerde::class.java)
//        props.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0)
//        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1000L)
//
//        // setting offset reset to earliest so that we can re-run the demo code with the same pre-loaded data
//        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
//
//        val builder = StreamsBuilder()
//
//        val views = builder.stream<String?, PageView?>(
//            "streams-pageview-input",
//            Consumed.with<String?, PageView?>(Serdes.String(), JSONSerde<PageView?>())
//        )
//
//        val users = builder.table<String?, UserProfile?>(
//            "streams-userprofile-input",
//            Consumed.with<String?, UserProfile?>(Serdes.String(), JSONSerde<UserProfile?>())
//        )
//
//        val duration24Hours: Duration = Duration.ofHours(24)
//
//        val regionCount = views
//            .leftJoin<UserProfile?, PageViewByRegion?>(users, ValueJoiner { view: PageView?, profile: UserProfile? ->
//                val viewByRegion = PageViewByRegion()
//                viewByRegion.user = view!!.user
//                viewByRegion.page = view.page
//
//                if (profile != null) {
//                    viewByRegion.region = profile.region
//                } else {
//                    viewByRegion.region = "UNKNOWN"
//                }
//                viewByRegion
//            })
//            .map<Any?, Any?>(KeyValueMapper { user: String?, viewRegion: PageViewByRegion? ->
//                KeyValue(
//                    viewRegion!!.region,
//                    viewRegion
//                )
//            })
//            .groupByKey(Grouped.with<Any?, Any?>(Serdes.String(), JSONSerde<JSONSerdeCompatible?>()))
//            .windowedBy<TimeWindow?>(
//                TimeWindows.ofSizeAndGrace(Duration.ofDays(7), duration24Hours).advanceBy(Duration.ofSeconds(1))
//            )
//            .count()
//            .toStream()
//            .map<WindowedPageViewByRegion?, RegionCount?>(KeyValueMapper { key: Windowed<Any?>?, value: Long? ->
//                val wViewByRegion = WindowedPageViewByRegion()
//                wViewByRegion.windowStart = key!!.window().start()
//                wViewByRegion.region = key.key()
//
//                val rCount = RegionCount()
//                rCount.region = key.key()
//                rCount.count = value!!
//                KeyValue(wViewByRegion, rCount)
//            })
//
//        // write to the result topic
//        regionCount.to(
//            "streams-pageviewstats-typed-output",
//            Produced.with<WindowedPageViewByRegion?, RegionCount?>(
//                JSONSerde<WindowedPageViewByRegion?>(),
//                JSONSerde<RegionCount?>()
//            )
//        )
//
//        val streams = KafkaStreams(builder.build(), props)
//        val latch = CountDownLatch(1)
//
//        // attach shutdown handler to catch control-c
//        Runtime.getRuntime().addShutdownHook(object : Thread("streams-pipe-shutdown-hook") {
//            override fun run() {
//                streams.close()
//                latch.countDown()
//            }
//        })
//
//        try {
//            streams.start()
//            latch.await()
//        } catch (e: Throwable) {
//            e.printStackTrace()
//            System.exit(1)
//        }
//        System.exit(0)
//    }
//
//    /**
//     * A serde for any class that implements [JSONSerdeCompatible]. Note that the classes also need to
//     * be registered in the `@JsonSubTypes` annotation on [JSONSerdeCompatible].
//     *
//     * @param <T> The concrete type of the class that gets de/serialized
//    </T> */
//    class JSONSerde<T : JSONSerdeCompatible?> : Serializer<T?>, Deserializer<T?>, Serde<T?> {
//        override fun configure(configs: MutableMap<String?, *>?, isKey: Boolean) {}
//
//        override fun deserialize(topic: String?, data: ByteArray?): T? {
//            if (data == null) {
//                return null
//            }
//
//            try {
//                return OBJECT_MAPPER.readValue<JSONSerdeCompatible?>(data, JSONSerdeCompatible::class.java) as T?
//            } catch (e: IOException) {
//                throw SerializationException(e)
//            }
//        }
//
//        override fun serialize(topic: String?, data: T?): ByteArray? {
//            if (data == null) {
//                return null
//            }
//
//            try {
//                return OBJECT_MAPPER.writeValueAsBytes(data)
//            } catch (e: Exception) {
//                throw SerializationException("Error serializing JSON message", e)
//            }
//        }
//
//        override fun close() {}
//
//        override fun serializer(): Serializer<T?> {
//            return this
//        }
//
//        override fun deserializer(): Deserializer<T?> {
//            return this
//        }
//
//        companion object {
//            private val OBJECT_MAPPER = ObjectMapper()
//        }
//    }
//
//    /**
//     * An interface for registering types that can be de/serialized with [JSONSerde].
//     */
//    // being explicit for the example
//    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "_t")
//    @JsonSubTypes(
//        JsonSubTypes.Type(value = PageView::class, name = "pv"),
//        JsonSubTypes.Type(value = UserProfile::class, name = "up"),
//        JsonSubTypes.Type(value = PageViewByRegion::class, name = "pvbr"),
//        JsonSubTypes.Type(value = WindowedPageViewByRegion::class, name = "wpvbr"),
//        JsonSubTypes.Type(value = RegionCount::class, name = "rc")
//    )
//    interface JSONSerdeCompatible
//
//    // POJO classes
//    class PageView : JSONSerdeCompatible {
//        var user: String? = null
//        var page: String? = null
//        var timestamp: Long? = null
//    }
//
//    class UserProfile : JSONSerdeCompatible {
//        var region: String? = null
//        var timestamp: Long? = null
//    }
//
//    class PageViewByRegion : JSONSerdeCompatible {
//        var user: String? = null
//        var page: String? = null
//        var region: String? = null
//    }
//
//    class WindowedPageViewByRegion : JSONSerdeCompatible {
//        var windowStart: Long = 0
//        var region: String? = null
//    }
//
//    class RegionCount : JSONSerdeCompatible {
//        var count: Long = 0
//        var region: String? = null
//    }
// }
