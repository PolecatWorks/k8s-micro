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


    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(
        topic: String?,
        data: Event?
    ): ByteArray? {
        if (data == null) return null
        if (topic == null) return null

        val outputStream = ByteArrayOutputStream()
        outputStream.write(MAGIC_BYTE.toInt())

        val schemaId = when (data) {
            is Event.Pizza -> 4
            is Event.Burger -> 5
        }
//            outputStream.write(schemaId)
        outputStream.write(ByteBuffer.allocate(SCHEMA_ID_SIZE).putInt(schemaId).array())

//            Avro.encodeToStream(data, outputStream)
        val output_bytes = when (data) {
            is Event.Burger -> Avro.Default.encodeToByteArray(data)
            is Event.Pizza ->  Avro.Default.encodeToByteArray(data)
        }

        println("temp serialized $output_bytes size ${output_bytes.size}")

        outputStream.write(output_bytes)

        return outputStream.toByteArray()
    }
}
