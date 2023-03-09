// This is free software, and you are welcome to redistribute it
// under certain conditions; See LICENSE file for details.

package com.polecatworks.kotlin.k8smicro

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.path
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addFileSource
import com.sksamuel.hoplite.addResourceSource
import mu.KotlinLogging
import java.nio.file.Paths

private val logger = KotlinLogging.logger {}

class K8sMicroCli : CliktCommand() {
    private val config by option(help = "Config file").file(canBeDir = false, mustBeReadable = true)
    private val secretDir by option(help = "Secrets dir").path(canBeFile = false, mustBeReadable = true)
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
        val defaultSecretDir = Paths.get(this.javaClass.classLoader.getResource("secrets").toURI())
        if (secretDir == null) {
            logger.info("Loading default secrets from resources dir")
            println("Defaulting to $defaultSecretDir")
        } else {
            println("using secre $secretDir")
        }
        val k8sMicroConfig = configBuilder.build()
            .loadConfigOrThrow<K8sMicroConfig>()
        logger.info("Config= $k8sMicroConfig")

        val k8sMicro = K8sMicro(version, k8sMicroConfig, secretDir ?: defaultSecretDir)

        k8sMicro.run()

        logger.info("K8sMicro is exiting")
    }
}

fun main(args: Array<String>) = K8sMicroCli().main(args)
