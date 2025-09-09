package com.polecatworks.kotlin.k8smicro.eventSerde

import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serializer

class EventSerde<T : Event>(
    private val eventSchemaManager: EventSchemaManager,
) : Serde<T> {
    private val avro4kSerializerb = EventSerializer(eventSchemaManager)
    private val avro4kDeserializerb = EventDeSerializer(eventSchemaManager)

    private val avro4kSerializer =
        Serializer<T> { subject, data ->
            avro4kSerializerb.serialize(subject, data)
        }

    private val avro4kDeserializer =
        Deserializer<T> { subject, data: ByteArray? ->
            avro4kDeserializerb.deserialize(subject, data) as T?
        }

    override fun serializer(): Serializer<T> = avro4kSerializer

    override fun deserializer(): Deserializer<T> = avro4kDeserializer
}
