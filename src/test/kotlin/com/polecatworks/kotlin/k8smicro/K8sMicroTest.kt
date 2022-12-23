package com.polecatworks.kotlin.k8smicro

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class K8sMicroTest {

    @Test
    fun testHello() {
        println("I am a useless test")
    }

    @Test
    fun testCoroutine() {
        runBlocking { // this: CoroutineScope
            coroutineScope {
                launch { // launch a new coroutine and continue
                    delay(1.seconds) // non-blocking delay for 1 second (default time unit is ms)
                    println("World!") // print after delay
                }
                launch { // launch a new coroutine and continue
                    delay(500.milliseconds) // non-blocking delay for 1 second (default time unit is ms)
                    println("World ish!") // print after delay
                }
            }
            println("Hello") // main coroutine continues while a previous one is delayed
        }
    }

    @Test
    fun testHealthService() {
        // Test startup and shutdown
        val healthService = HealthService()
        val healthThread = thread {
            healthService.start()
        }

        Thread.sleep(2.seconds.inWholeMilliseconds)
        healthService.stop()
        healthThread.join()
        println("Done with test")
    }
}
