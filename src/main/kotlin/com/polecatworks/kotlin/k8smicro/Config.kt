package com.polecatworks.kotlin.k8smicro

// data class Database(val host: String, val port: Int, val user: String, val pass: String)
data class WebServer(val port: Int, val prefix: String)
data class Config(val env: String, val webserver: WebServer)
