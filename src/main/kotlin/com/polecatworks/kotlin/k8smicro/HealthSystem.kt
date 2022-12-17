package com.polecatworks.kotlin.k8smicro

import kotlinx.serialization.Serializable
import mu.KotlinLogging
import java.time.LocalDateTime
import kotlin.time.Duration
// import java.time.LocalDateTime
// import com.polecatworks.kotlin.k8smicro.serialisers.LocalDateTimeSerializer

// Place definition above class declaration to make field static
private val logger = KotlinLogging.logger {}

// @Serializable
// data class HealthResult(
//    val name: String,
// //    @Serializable(LocalDateTimeSerializer::class)
// //    val lastUpdated: LocalDateTime,
//    val succeeded: Boolean = true,
// )

@Serializable
data class HealthSystemResult(
    val name: String,
    val valid: Boolean,
    val detail: List<HealthCheckResult>
)

class HealthSystem {
    val alive = mutableListOf<HealthCheck>()
    val ready = mutableListOf<HealthCheck>()
    init {
        logger.info { "starting HealthSystem" }
    }

    public fun checkAlive(): HealthSystemResult {
        val myNow = LocalDateTime.now()
        val results = alive.map { value -> value.check(myNow) }

        return HealthSystemResult("alive", results.all { result -> result.valid }, results)
    }
    public fun checkReady(): HealthSystemResult {
        val myNow = LocalDateTime.now()
        val results = ready.map { value -> value.check(myNow) }

        return HealthSystemResult("ready", results.all { result -> result.valid }, results)
    }

    fun registerAlive(name: String, margin: Duration): HealthCheck {
        val newHealth = HealthCheck(name, margin)
        alive.add(newHealth)
        return newHealth
    }
    fun deregisterAlive(oldHealth: HealthCheck): Boolean {
        return alive.remove(oldHealth)
    }
    fun registerReady(name: String, margin: Duration): HealthCheck {
        val newHealth = HealthCheck(name, margin)
        ready.add(newHealth)
        return newHealth
    }
    fun deregisterReady(oldHealth: HealthCheck): Boolean {
        return ready.remove(oldHealth)
    }
}
