package com.polecatworks.kotlin.k8smicro.eventSerde

import com.github.avrokotlin.avro4k.Avro
import kotlinx.serialization.decodeFromByteArray
import org.apache.kafka.common.serialization.Deserializer
import java.nio.ByteBuffer

class EventDeSerializer: Deserializer<Event> {

    companion object {
        const val MAGIC_BYTE: Byte = 0x0
        const val SCHEMA_ID_SIZE = 4
    }


    override fun deserialize(
        topic: String?,
        data: ByteArray?
    ): Event? {
        if (data == null) return null
        if (topic == null) return null
        if (data.size < SCHEMA_ID_SIZE + 1 + 1) throw IllegalArgumentException("Data block is too short")


        val magicByte =data[0];

        if (magicByte != MAGIC_BYTE) {
            throw IllegalArgumentException("Invalid magic byte: $magicByte")
        }


        val schemaId = ByteBuffer.wrap(data.sliceArray(1..SCHEMA_ID_SIZE)).getInt()


        val objBytes = data.sliceArray(SCHEMA_ID_SIZE+1..<data.size)

        val returnEvent = when (schemaId) {
            4 -> Avro.decodeFromByteArray<Event.Pizza>(objBytes)
            5 -> Avro.decodeFromByteArray<Event.Burger>(objBytes)
            else -> throw IllegalArgumentException("SchemaId not known")
        }

        return returnEvent
    }

}
