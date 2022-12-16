package com.polecatworks.kotlin.k8smicro

import mu.KotlinLogging
import kotlinx.serialization.Serializable
//import java.time.LocalDateTime
//import com.polecatworks.kotlin.k8smicro.serialisers.LocalDateTimeSerializer
import com.polecatworks.kotlin.k8smicro.HealthCheck

// Place definition above class declaration to make field static
private val logger = KotlinLogging.logger {}


@Serializable
data class HealthResult(
    val name: String,
//    @Serializable(LocalDateTimeSerializer::class)
//    val lastUpdated: LocalDateTime,
    val succeeded: Boolean = true
)

class HealthSystem {
    val alive = listOf<HealthCheck>()
    val ready = listOf<HealthCheck>()
    init {
        logger.info { "starting HealthSystem" }
    }

    public fun getAlive(): Pair<Boolean, Boolean> {
        return Pair(true, true)
    }

}
