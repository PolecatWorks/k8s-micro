package com.polecatworks.kotlin.k8smicro.eventSerde

import com.github.avrokotlin.avro4k.Avro
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import org.apache.kafka.common.serialization.Serializer
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class EventSerializer : Serializer<Event> {
    companion object {
        const val MAGIC_BYTE: Byte = 0x0
        const val SCHEMA_ID_SIZE = 4
    }

    public val schemaManager: EventSchemaManager

    constructor(schemaManager: EventSchemaManager) {
        this.schemaManager = schemaManager
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(
        topic: String?,
        data: Event?,
    ): ByteArray? {
        if (topic == null) return null
        if (data == null) return null

        val outputStream = ByteArrayOutputStream()
        outputStream.write(MAGIC_BYTE.toInt())

        val schemaId = schemaManager.getSchemaIdForEvent(data)!!
        outputStream.write(ByteBuffer.allocate(SCHEMA_ID_SIZE).putInt(schemaId).array())

        val outputBytes =
            when (data) {
                is Event.Burger -> Avro.encodeToByteArray<Event.Burger>(data)
                is Event.Pizza -> Avro.encodeToByteArray<Event.Pizza>(data)
                // When is exhaustive so compiler will fault if we do not complete all options
            }

        outputStream.write(outputBytes)

        return outputStream.toByteArray()
    }
}
