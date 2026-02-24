# Automated Testing Strategy for K8s-Micro

This document outlines the proposed strategy for automating integration and end-to-end testing for the `k8s-micro` service. The goal is to ensure the reliability of the Kafka event processing logic, aggregation state management, and API endpoints.

## Objectives

1.  **Integration Testing**: Verify that the application correctly interacts with external dependencies (Kafka, Schema Registry).
2.  **State Management**: Ensure aggregates (Chaser, Billing) are correctly updated based on event streams.
3.  **API Validation**: Confirm that the REST API accurately reflects the internal state of the aggregates.
4.  **Schema Evolution**: Ensure backward compatibility of Avro schemas.
5.  **Resilience**: Verify system behavior under failure conditions (e.g., duplicate events, out-of-order messages).

## Technology Stack

*   **Test Framework**: [JUnit 5](https://junit.org/junit5/)
*   **Infrastructure Management**: [TestContainers](https://testcontainers.com/) (Kafka, Schema Registry)
*   **Assertion Library**: [AssertJ](https://assertj.github.io/doc/) or Kotlin-test
*   **Async Testing**: [Awaitility](http://www.awaitility.org/) (for polling state changes)
*   **HTTP Client**: Ktor Client or standard Java HTTP client for API verification.

## Architecture

The tests will spin up a complete environment using TestContainers. This ensures isolation and reproducibility.

1.  **Start Containers**:
    *   `confluentinc/cp-zookeeper`
    *   `confluentinc/cp-kafka`
    *   `confluentinc/cp-schema-registry`
2.  **Start Application**:
    *   Run the `K8sMicro` application within the test JVM, pointing it to the dynamic ports of the containers.
3.  **Execute Tests**:
    *   Produce events to the input topic.
    *   Wait for processing (using Awaitility).
    *   Verify results via:
        *   Kafka Consumer (for output topics).
        *   REST API (for state queries).

## Test Scenarios

### 1. Chaser Aggregation (Happy Path)
*   **Input**: Multiple `Chaser` events for the same `key` with different timestamps and TTLs.
*   **Expected Output**:
    *   An `Event.Aggregate` in the output topic.
    *   `GET /chaser/{key}` returns the correct count, latest timestamp, and longest TTL.
*   **Steps**:
    1.  Produce `Chaser(name="A", sent=100, ttl=10)`
    2.  Produce `Chaser(name="B", sent=200, ttl=20)`
    3.  Verify API response: `count=2`, `latest=200`, `longest=20`.

### 2. Billing Lifecycle (Happy Path)
*   **Input**:
    1.  `Bill` event.
    2.  `PaymentRequest` event matching the bill.
*   **Expected Output**:
    *   `GET /billing/{key}` shows the bill and the payment request.
    *   `errored` flag is `false`.

### 3. Payment Failure (Resilience)
*   **Input**:
    1.  `Bill` event.
    2.  `PaymentFailed` event.
*   **Expected Output**:
    *   `GET /billing/{key}` shows the failed payment.
    *   `errored` flag might be `true` (depending on business logic).

### 4. Duplicate Events (Idempotency)
*   **Input**:
    1.  `Bill` event (ID=1).
    2.  `Bill` event (ID=1) again.
*   **Expected Output**:
    *   System should handle duplicates gracefully (e.g., ignore or update idempotently).
    *   Aggregates count should remain correct.

### 5. Out-of-Order Events
*   **Input**:
    1.  `PaymentRequest` (for Bill ID=1).
    2.  `Bill` (ID=1) arrives *later*.
*   **Expected Output**:
    *   System handles the late arrival correctly.
    *   Final state is consistent.

## Implementation Plan (Phased)

### Phase 1: Infrastructure Setup
*   Add dependencies: `testcontainers-kafka`, `awaitility`.
*   Create a base test class `AbstractIntegrationTest` that manages the container lifecycle.

### Phase 2: Core Logic Tests
*   Implement tests for `Chaser` aggregation.
*   Implement tests for `Billing` aggregation.

### Phase 3: Edge Cases & API
*   Add tests for duplicates and failures.
*   Add comprehensive API verification.

## Example Test Structure (Pseudo-code)

```kotlin
@Testcontainers
class BillingIntegrationTest {

    @Container
    val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
    // ... schema registry container ...

    @Test
    fun `test billing lifecycle`() {
        // 1. Setup
        val app = startApp(kafka.bootstrapServers, schemaRegistry.url)
        val producer = createProducer(kafka.bootstrapServers)

        // 2. Execute
        producer.send("input-topic", key="user-123", value=Bill(...))
        producer.send("input-topic", key="user-123", value=PaymentRequest(...))

        // 3. Verify
        await().atMost(10, TimeUnit.SECONDS).untilAsserted {
            val response = client.get("http://localhost:8080/billing/user-123")
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val aggregate = response.body<BillAggregate>()
            assertThat(aggregate.paymentRequests).hasSize(1)
            assertThat(aggregate.bill).isNotNull
        }
    }
}
```
