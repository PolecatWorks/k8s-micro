package com.polecatworks.kotlin.k8smicro.health

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource.Monotonic.ValueTimeMark
import kotlin.time.TimeSource.Monotonic.markNow

@Serializable
data class HealthCheckResult(val name: String, val valid: Boolean)

@OptIn(ExperimentalTime::class)
interface IHealthCheck {
    val name: String
    val margin: Duration
    fun check(time: ValueTimeMark): HealthCheckResult
}

@OptIn(ExperimentalTime::class)
class HealthCheck(override val name: String, override val margin: Duration) : IHealthCheck {
    var latest = markNow()

    fun kick() {
        latest = markNow()
    }
    override fun check(time: ValueTimeMark): HealthCheckResult {
        return HealthCheckResult(name, latest.plus(margin) >= time)
    }
}
