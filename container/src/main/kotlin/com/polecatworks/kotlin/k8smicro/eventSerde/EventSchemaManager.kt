package com.polecatworks.kotlin.k8smicro.eventSerde

import com.github.avrokotlin.avro4k.Avro
import com.polecatworks.kotlin.k8smicro.KafkaSchemaRegistryApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer

class EventSchemaManager {
    constructor(kafkaSchemaRegistryApi: KafkaSchemaRegistryApi) {
        this.kafkaSchemaRegistryApi = kafkaSchemaRegistryApi
    }

    private val kafkaSchemaRegistryApi: KafkaSchemaRegistryApi

    private val classToSchemaId = mutableMapOf<Class<*>, Int>()
    private val schemaIdToClass = mutableMapOf<Int, Class<*>>()

    fun registerSchema(
        clazz: Class<*>,
        schemaId: Int,
        /**
         * Manages the event schema for the application.
         *
         * This function is responsible for handling the logic related to event schema management,
         * such as registering, updating, or validating event schemas used in the system.
         *
         * @throws IllegalArgumentException if schema management fails.
         */
    ) {
        if (classToSchemaId.containsKey(clazz)) {
            throw IllegalArgumentException("Schema for class ${clazz.name} is already registered.")
        }
        classToSchemaId[clazz] = schemaId

        if (schemaIdToClass.containsKey(schemaId)) {
            throw IllegalArgumentException("Schema ID $schemaId is already registered for class ${schemaIdToClass[schemaId]?.name}.")
        }
        schemaIdToClass[schemaId] = clazz
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun registerAllSchemas(subject: String) {
        for (myClass in Event.subClasses()) {
            val schema = Avro.schema(myClass.serializer().descriptor)

            val schemaId = kafkaSchemaRegistryApi.registerSchema(subject, schema.toString())

            registerSchema(myClass.java, schemaId)
        }
    }

    fun getSchemaIdForEvent(data: Event): Int? = getSchemaIdForClass(data.javaClass)

    fun getSchemaIdForClass(clazz: Class<*>): Int? = classToSchemaId[clazz]

    fun getClassForSchemaId(schemaId: Int): Class<*>? = schemaIdToClass[schemaId]
}
