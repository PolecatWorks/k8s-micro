// This is free software, and you are welcome to redistribute it
// under certain conditions; See LICENSE file for details.

package com.polecatworks.kotlin.k8smicro

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addFileSource
import com.sksamuel.hoplite.addResourceSource
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.contentnegotiation.*
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

class K8sMicroCli : CliktCommand() {
    private val config by option(help = "Config file").file(canBeFile = true)
    private var running = AtomicBoolean(true)

    override fun run() {
        val version = System.getenv("VERSION") ?: "0.0.0"
        val configBuilder = ConfigLoaderBuilder.default()
        if (config == null) {
            logger.info("Loading default config from resource")
            configBuilder.addResourceSource("/k8smicro-config.yaml")
        } else {
            logger.info("Loading config from file: $config")
            configBuilder.addFileSource(config!!)
        }
        val k8sMicroConfig = configBuilder.build()
            .loadConfigOrThrow<K8sMicroConfig>()
        logger.info("Config= $k8sMicroConfig")

        val k8sMicro = K8sMicro(version, k8sMicroConfig)

        k8sMicro.run()

        logger.info("K8sMicro is exiting")
    }
}

fun main(args: Array<String>) = K8sMicroCli().main(args)
