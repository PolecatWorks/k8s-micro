// This is free software, and you are welcome to redistribute it
// under certain conditions; See LICENSE file for details.

package com.polecatworks.kotlin.k8smicro

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.path
import com.polecatworks.kotlin.k8smicro.utils.SecretFilesPreProcessor
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addFileSource
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class K8sMicroCli : CliktCommand() {
    private val config by option(help = "Config file").file(canBeDir = false, mustBeReadable = true).required()
    private val secretDir by option(help = "Secrets dir").path(canBeFile = false, mustBeReadable = true).required()

    override fun run() {
        val version = System.getenv("VERSION") ?: "0.0.0"

        val configBuilder =
            ConfigLoaderBuilder
                .default()
                .addFileSource(config)

        val secretFilesPreprocessor = SecretFilesPreProcessor(secretDir)

        val k8sMicroConfig =
            configBuilder
                .addPreprocessor(secretFilesPreprocessor)
                .build()
                .loadConfigOrThrow<K8sMicroConfig>()
        logger.info("Config= $k8sMicroConfig")

        val k8sMicro = K8sMicro(version, k8sMicroConfig)

        k8sMicro.run()

        logger.info("K8sMicro is exiting")
    }
}

fun main(args: Array<String>) = K8sMicroCli().main(args)
