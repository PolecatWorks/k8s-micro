package com.polecatworks.kotlin.k8smicro.health

import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource.Monotonic.ValueTimeMark
import kotlin.time.TimeSource.Monotonic.markNow

@Serializable
data class HealthCheckResult(val name: String, val valid: Boolean)

@OptIn(ExperimentalTime::class)
interface IHealthCheck {
    val name: String
    fun check(time: ValueTimeMark): HealthCheckResult
}

@OptIn(ExperimentalTime::class)
class AliveMarginCheck(override val name: String, val margin: Duration) : IHealthCheck {
    var latest = markNow()

    fun kick() {
        latest = markNow()
    }
    override fun check(time: ValueTimeMark): HealthCheckResult {
        return HealthCheckResult(name, latest.plus(margin) >= time)
    }
}

class ReadyStateCheck(override val name: String) : IHealthCheck {
    var state = AtomicBoolean(false)

    /**
     * Set to busy and return true if state change occurred
     */
    fun busy(): Boolean {
        return state.getAndSet(false)
    }

    /**
     * Set to ready and return true if state change occurred
     */
    fun ready(): Boolean {
        return !state.getAndSet(true)
    }

    @OptIn(ExperimentalTime::class)
    override fun check(time: ValueTimeMark): HealthCheckResult {
        return HealthCheckResult(name, state.get())
    }
}
