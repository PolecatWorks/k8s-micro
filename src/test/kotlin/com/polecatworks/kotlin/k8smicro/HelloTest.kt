package com.polecatworks.kotlin.k8smicro

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.testing.*
import org.junit.Test

class HelloTest {

    @Test
    fun testHello() {
        println("I am a useless test")
    }
}
