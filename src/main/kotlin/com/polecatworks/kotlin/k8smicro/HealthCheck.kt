package com.polecatworks.kotlin.k8smicro

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource.Monotonic.ValueTimeMark
import kotlin.time.TimeSource.Monotonic.markNow

@Serializable
data class HealthCheckResult(val name: String, val valid: Boolean)

@OptIn(ExperimentalTime::class)
class HealthCheck(val name: String, val margin: Duration) {
    var latest = markNow()

    public fun kick() {
        latest = markNow()
    }
    public fun check(time: ValueTimeMark): HealthCheckResult {
        return HealthCheckResult(name, latest.plus(margin) >= time)
    }
}
