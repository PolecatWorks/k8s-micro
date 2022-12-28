package com.polecatworks.kotlin.k8smicro

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val aliveAccess = Mutex() // Not a big penalty since adding/removing should be rare
    private val readyAccess = Mutex() // Not a big penalty since adding/removing should be rare
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

    suspend fun registerAlive(myHealth: IHealthCheck): Boolean {
        aliveAccess.withLock {
            return alive.add(myHealth)
        }
    }
    suspend fun deregisterAlive(myHealth: IHealthCheck): Boolean {
        aliveAccess.withLock {
            return alive.remove(myHealth)
        }
    }
    suspend fun registerReady(myHealth: IHealthCheck): Boolean {
        readyAccess.withLock {
            return ready.add(myHealth)
        }
    }
    suspend fun deregisterReady(myHealth: HealthCheck): Boolean {
        readyAccess.withLock {
            return ready.remove(myHealth)
        }
    }
}
