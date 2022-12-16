package com.polecatworks.kotlin.k8smicro

import mu.KotlinLogging

// Place definition above class declaration to make field static
private val logger = KotlinLogging.logger {}

class HealthSystem {
    val alive = listOf<HealthCheck>()
    val ready = listOf<HealthCheck>()
    init {
        logger.info { "starting HealthSystem" }
    }
}
