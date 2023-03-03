package com.polecatworks.kotlin.k8smicro
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

data class KafkaSchemaRegistryConfig(
    val hosturl: String
)

class KafkaSchemaRegistryApi(engine: HttpClientEngine, config: KafkaSchemaRegistryConfig) {

    private val httpClient = HttpClient(engine) {
        install(Logging)
        install(ContentNegotiation) {
            json()
        }
    }

    private val schemaRegistryUrl = "${config.hosturl}/subjects"

    suspend fun checkConnection(): String {
        return httpClient.get("http://www.google.com").body()
    }

    suspend fun registerSchema(subject: String, schema: String): Int {
        val response = httpClient.post {
            url("$schemaRegistryUrl/$subject/versions")
            setBody(schema)
        }
        return response.body()
    }

    suspend fun getLatestSchema(subject: String): String {
        return httpClient.get {
            url("$schemaRegistryUrl/$subject/versions/latest/schema")
        }.toString()
    }

    suspend fun getSchemaByVersion(subject: String, version: Int): String {
        return httpClient.get {
            url("$schemaRegistryUrl/$subject/versions/$version/schema")
        }.body()
    }

    suspend fun checkCompatibility(subject: String, schema: String): Boolean {
        val response = httpClient.post {
            url("$schemaRegistryUrl/$subject/versions/latest")
            setBody(schema)
        }
        return response.body()
    }

    suspend fun updateCompatibility(subject: String, compatibilityLevel: String): String {
        return httpClient.put {
            url("$schemaRegistryUrl/$subject/config")
            setBody("{\"compatibility\": \"$compatibilityLevel\"}")
        }.body()
    }
}
