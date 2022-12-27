package com.polecatworks.kotlin.k8smicro

import kotlin.time.Duration

// data class Database(val host: String, val port: Int, val user: String, val pass: String)
data class WebServer(
    val port: Int,
    val prefix: String
)
data class RandomThread(
    val sleepTime: Duration
)
data class K8sMicroApp(
    val threadSleep: Duration
)

/**
 * Configuration for K8sMicro
 */
data class K8sMicroConfig(
    val env: String,
    val webserver: WebServer,
    val randomThread: RandomThread,
    val app: K8sMicroApp
)
