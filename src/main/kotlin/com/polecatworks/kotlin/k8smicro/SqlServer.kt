package com.polecatworks.kotlin.k8smicro

import com.polecatworks.kotlin.k8smicro.health.AliveMarginCheck
import com.polecatworks.kotlin.k8smicro.health.HealthSystem
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addFileSource
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import java.sql.ResultSet
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

private val logger = KotlinLogging.logger {}

data class SqlServerConfig(
    val jdbcUrl: String,
    val driver: String,
    val healthSleep: Duration,
    val threadSleep: Duration
) {
    init {
        check(healthSleep > threadSleep * 2) { "healthSleep($healthSleep) must be greater than 2*threadSleep($threadSleep)" }
        val allowedDrivers = setOf("org.postgresql.Driver")
        check(allowedDrivers.contains(driver)) { "driver ($driver) must be one of $allowedDrivers" }
    }
}

data class SqlServerSecret(
    val username: String,
    val password: String
)
class SqlServer(
    private val config: SqlServerConfig,
    private val secretDir: Path,
    val health: HealthSystem,
    val running: AtomicBoolean
) {
    val secret: SqlServerSecret
    init {
        val configBuilder = ConfigLoaderBuilder.default()
        configBuilder.addFileSource(secretDir.resolve("database.yaml").toFile())
        secret = configBuilder.build().loadConfigOrThrow()
    }

    val database = Database

    @OptIn(ExperimentalTime::class)
    suspend fun start() = coroutineScope {
        logger.info("Starting Sql")
        val myAlive = AliveMarginCheck("SQL Server", config.healthSleep, false)
        health.registerAlive(myAlive)

        database.connect(
            url = config.jdbcUrl,
            driver = config.driver,
            user = secret.username,
            password = secret.password
        ) // Does not actuall connect. Just stores the connection info

        launch {
            //
            var checkinTime = TimeSource.Monotonic.markNow() // Start out immediately
            while (running.get()) {
                val timeNow = TimeSource.Monotonic.markNow()
                if (checkinTime < timeNow) {
                    if (checkConnection()) {
                        logger.info("Confirmed connection to SQL Server")
                        myAlive.kick()
                        checkinTime = timeNow + config.healthSleep - config.threadSleep * 2
                    } else {
                        logger.warn("Cannot connect to SQL Server")
                    }
                }
                delay(config.threadSleep)
            }
            running.set(false)
            health.deregisterAlive(myAlive) // TODO: Consider to not deregister and leave the liveness in place
            logger.info("Stopped alive check on SQL Server")
        }
    }

    private suspend fun checkConnection(): Boolean {
        var valid_connection = false
        try {
            transaction {
                // addLogger(StdOutSqlLogger)

//                "SPS 1=1 as alive".execAndMap { rs ->
                "select 1=1 as alive".execAndMap { rs ->
                    "alive" to rs.getBoolean("alive")
                }
                valid_connection = true
                println("DB Connection achieved")
            }
        } catch (e: ExposedSQLException) {
            println("Did not get connection to DB")
        } catch (e: Exception) {
            println("Did not get connection to DB")
        }
        return valid_connection
    }
}

fun <T : Any> String.execAndMap(transform: (ResultSet) -> T): List<T> {
    val result = arrayListOf<T>()
    TransactionManager.current().exec(this) { rs ->
        while (rs.next()) {
            result += transform(rs)
        }
    }
    return result
}
