package com.polecatworks.kotlin.k8smicro

import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import kotlin.time.Duration

@Serializable
data class HealthCheckResult(val name: String, val valid: Boolean)

class HealthCheck(val name: String, val margin: Duration) {
    var latest = LocalDateTime.now()

    public fun kick() {
        latest = LocalDateTime.now()
    }
    public fun check(time: LocalDateTime): HealthCheckResult {
//        return latest+ margin < time

        return HealthCheckResult(name, true)
    }
}
