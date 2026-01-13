package com.polecatworks.kotlin.k8smicro

import com.polecatworks.kotlin.k8smicro.eventSerde.Event
import com.polecatworks.kotlin.k8smicro.eventSerde.EventSchemaManager
import com.polecatworks.kotlin.k8smicro.eventSerde.EventSerde
import com.polecatworks.kotlin.k8smicro.health.HealthSystem
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.TopologyTestDriver
import org.junit.Test
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class KafkaProcessorTest {
    private val schemaMap =
        mapOf(
            "Chaser" to 1,
            "Aggregate" to 2,
            "Bill" to 3,
            "PaymentRequest" to 4,
            "PaymentFailed" to 5,
            "BillAggregate" to 6,
        )

    private val mockEngine =
        MockEngine { request ->
            when (request.url.encodedPath) {
                "/config" -> {
                    respond(
                        content = ByteReadChannel("""{"compatibilityLevel": "FULL"}"""),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                "/subjects/test-topic-value/versions" -> {
                    val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                    val schema = (body["schema"] as? JsonPrimitive)?.content ?: ""
                    val schemaContent = Json.parseToJsonElement(schema).jsonObject
                    val schemaName = (schemaContent["name"] as? JsonPrimitive)?.content ?: ""
                    val schemaId = schemaMap[schemaName] ?: 0
                    respond(
                        content = ByteReadChannel("""{"id": $schemaId}"""),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else ->
                    respond(
                        content = ByteReadChannel("""{"error_code":404,"message":"Not found"}"""),
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
            }
        }

    private val config =
        KafkaProcessorConfig(
            hostUrl = "http://localhost:8080",
            schemaRegistry = KafkaSchemaRegistryConfig("http://localhost:8081", 60.seconds),
            readTopic = "test-topic",
            writeTopic = "output-topic",
            billingOutputTopic = "billing-topic",
            autoOffsetReset = "earliest",
        )

    private val health = HealthSystem()
    private val running = AtomicBoolean(true)

    @Test
    fun testBillingAggregation() {
        val processor = KafkaProcessor(config, health, mockEngine, running, "localhost:8080")
        val eventSerde = EventSerde()
        val eventSchemaManager = EventSchemaManager(processor.schemaRegistryApi)

        // Mocking schema registration since we don't have a real schema registry
        eventSchemaManager.registerSchema(Event.Bill::class.java, 3)
        eventSchemaManager.registerSchema(Event.PaymentRequest::class.java, 4)
        eventSchemaManager.registerSchema(Event.PaymentFailed::class.java, 5)
        eventSchemaManager.registerSchema(Event.BillAggregate::class.java, 6)

        eventSerde.setSchemaManager(eventSchemaManager)

        val props =
            Properties().apply {
                setProperty("application.id", "test")
                setProperty("bootstrap.servers", "localhost:9092")
            }

        val builder = StreamsBuilder()
        val topology = processor.buildTopology(builder, eventSerde)

        TopologyTestDriver(topology, props).use { driver ->
            val inputTopic = driver.createInputTopic(config.readTopic, Serdes.String().serializer(), eventSerde.serializer())

            val billId = "BILL-1"
            val bill =
                Event.Bill(
                    billId = billId,
                    customerId = "CUST-1",
                    orderId = "ORDER-1",
                    amountCents = 1000,
                    currency = "USD",
                    issuedAt = 100L,
                    dueDate = 200L,
                )

            // 1. Add a bill
            inputTopic.pipeInput(billId, bill)

            val store = driver.getKeyValueStore<String, Event>("billing")
            val agg = store.get(billId) as? Event.BillAggregate
            assertNotNull(agg)
            assertEquals(bill, agg.bill)
            assertEquals(false, agg.errored)

            // 2. Add a payment request
            val payReq =
                Event.PaymentRequest(
                    paymentId = "PAY-1",
                    billId = billId,
                    customerId = "CUST-1",
                    amountCents = 1000,
                    currency = "USD",
                    requestedAt = 110L,
                )
            inputTopic.pipeInput(billId, payReq)

            val aggWithPay = store.get(billId) as Event.BillAggregate
            assertEquals(1, aggWithPay.paymentRequests.size)
            assertEquals(payReq, aggWithPay.paymentRequests[0])

            // 3. Add a payment failure
            val payFail =
                Event.PaymentFailed(
                    paymentId = "PAY-1",
                    billId = billId,
                    customerId = "CUST-1",
                    failureReason = "NSF",
                    failedAt = 120L,
                )
            inputTopic.pipeInput(billId, payFail)

            val aggWithFail = store.get(billId) as Event.BillAggregate
            assertEquals(payFail, aggWithFail.lastPaymentFailed)
            assertEquals(false, aggWithFail.errored)

            // 4. Add another payment request (should reset failure)
            val payReq2 =
                Event.PaymentRequest(
                    paymentId = "PAY-1", // duplicate ID should be ignored by list but still reset fail
                    billId = billId,
                    customerId = "CUST-1",
                    amountCents = 1000,
                    currency = "USD",
                    requestedAt = 130L,
                )
            inputTopic.pipeInput(billId, payReq2)

            val aggReset = store.get(billId) as Event.BillAggregate
            assertEquals(null, aggReset.lastPaymentFailed)
            assertEquals(1, aggReset.paymentRequests.size) // No change because duplicate ID

            // 5. Add a duplicate bill with different data (should error)
            val badBill = bill.copy(amountCents = 2000)
            inputTopic.pipeInput(billId, badBill)

            val aggErrored = store.get(billId) as Event.BillAggregate
            assertEquals(true, aggErrored.errored)
            assertEquals(badBill, aggErrored.bill)
        }
    }
}
