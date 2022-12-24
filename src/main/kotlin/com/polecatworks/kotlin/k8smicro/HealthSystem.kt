package com.polecatworks.kotlin.k8smicro

import kotlinx.serialization.Serializable
import mu.KotlinLogging
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource.Monotonic.ValueTimeMark

// Place definition above class declaration to make field static
private val logger = KotlinLogging.logger {}

@Serializable
data class HealthSystemResult(
    val name: String,
    val valid: Boolean,
    val detail: List<HealthCheckResult>
)

/**
 * A health system for k8s
 *
 * Manage the ready and alive objects for a k8s service.
 * Returns check for alive and ready when requested and calculates this on demand.
 */
@OptIn(ExperimentalTime::class)
class HealthSystem {
    private val alive = mutableListOf<IHealthCheck>()
    private val ready = mutableListOf<IHealthCheck>()
    init {
        logger.info { "starting HealthSystem" }
    }

    @OptIn(ExperimentalTime::class)
    fun checkAlive(myNow: ValueTimeMark): HealthSystemResult {
        val results = alive.map { value -> value.check(myNow) }

        return HealthSystemResult("alive", results.all { result -> result.valid }, results)
    }
    fun checkReady(myNow: ValueTimeMark): HealthSystemResult {
        val results = ready.map { value -> value.check(myNow) }

        return HealthSystemResult("ready", results.all { result -> result.valid }, results)
    }

    fun registerAlive(myHealth: IHealthCheck): IHealthCheck {
        alive.add(myHealth)
        return myHealth
    }
    fun deregisterAlive(myHealth: IHealthCheck): Boolean {
        return alive.remove(myHealth)
    }
    fun registerReady(myHealth: IHealthCheck): IHealthCheck {
        ready.add(myHealth)
        return myHealth
    }
    fun deregisterReady(myHealth: HealthCheck): Boolean {
        return ready.remove(myHealth)
    }
}
