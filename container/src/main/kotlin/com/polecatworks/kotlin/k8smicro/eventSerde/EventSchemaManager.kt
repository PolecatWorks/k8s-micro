package com.polecatworks.kotlin.k8smicro.eventSerde

import com.polecatworks.kotlin.k8smicro.KafkaSchemaRegistryApi

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
    ) {
        classToSchemaId[clazz] = schemaId
        schemaIdToClass[schemaId] = clazz
    }

    fun getSchemaIdForClass(clazz: Class<*>): Int? = classToSchemaId[clazz]

    fun getClassForSchemaId(schemaId: Int): Class<*>? = schemaIdToClass[schemaId]
}
