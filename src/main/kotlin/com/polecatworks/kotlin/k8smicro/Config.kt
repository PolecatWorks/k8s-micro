package com.polecatworks.kotlin.k8smicro

import java.time.Duration

// data class Database(val host: String, val port: Int, val user: String, val pass: String)
data class WebServer(val port: Int, val prefix: String)
data class RandomThread(val sleepTime: Duration)
data class Config(
    val env: String,
    val webserver: WebServer,
    val randomThread: RandomThread
)
