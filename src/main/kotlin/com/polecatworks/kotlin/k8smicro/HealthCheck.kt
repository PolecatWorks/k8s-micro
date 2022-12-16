package com.polecatworks.kotlin.k8smicro

import java.sql.Time
import java.time.Duration
import java.time.LocalDate

class HealthCheck(val name: String, val margin: Duration) {
    var latest = LocalDate.now()

    public fun kick() {
        latest = LocalDate.now()
    }
    public fun check(time: Time): Boolean {
        val tempTime = Duration.ofSeconds(3)
        return tempTime > margin
    }
}
