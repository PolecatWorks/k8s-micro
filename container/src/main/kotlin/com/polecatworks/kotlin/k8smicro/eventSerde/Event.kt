package com.polecatworks.kotlin.k8smicro.eventSerde

import com.github.avrokotlin.avro4k.AvroDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/**
 * Sealed class representing domain events and aggregates.
 *
 * This class uses Kotlin Serialization and Avro4k for serialization.
 */
@Serializable
sealed class Event {
    /**
     * Represents a Chaser event.
     *
     * @property name The name associated with the chaser.
     * @property id The unique identifier.
     * @property sent Timestamp when it was sent.
     * @property ttl Time to live.
     * @property previous Previous timestamp if any.
     */
    @Serializable
    @SerialName("com.polecatworks.chaser.Chaser")
    data class Chaser(
        val name: String,
        val id: String,
        val sent: Long,
        val ttl: Long,
        val previous: Long?,
    ) : Event()

    /**
     * Represents an aggregated state of Chaser events.
     *
     * @property names List of names encountered.
     * @property count Total count of events.
     * @property latest Latest timestamp.
     * @property longest Longest duration observed.
     */
    @Serializable
    @SerialName("com.polecatworks.chaser.Aggregate")
    data class Aggregate(
        val names: List<String>,
        val count: Long,
        val latest: Long?,
        val longest: Long?,
    ) : Event()

    /**
     * Represents a Bill event.
     *
     * @property billId Unique bill identifier.
     * @property customerId Customer identifier.
     * @property orderId Order identifier.
     * @property amountCents Amount in cents.
     * @property currency Currency code.
     * @property issuedAt Timestamp when issued.
     * @property dueDate Timestamp when due.
     */
    @Serializable
    @SerialName("com.polecatworks.billing.Bill")
    data class Bill(
        val billId: String,
        val customerId: String,
        val orderId: String,
        val amountCents: Long,
        val currency: String,
        val issuedAt: Long,
        val dueDate: Long,
    ) : Event()

    /**
     * Represents a Payment Request event.
     *
     * @property paymentId Unique payment identifier.
     * @property billId Bill identifier.
     * @property customerId Customer identifier.
     * @property amountCents Amount in cents.
     * @property currency Currency code.
     * @property requestedAt Timestamp when requested.
     */
    @Serializable
    @SerialName("com.polecatworks.billing.PaymentRequest")
    data class PaymentRequest(
        val paymentId: String,
        val billId: String,
        val customerId: String,
        val amountCents: Long,
        val currency: String,
        val requestedAt: Long,
    ) : Event()

    /**
     * Represents a Payment Failed event.
     *
     * @property paymentId Unique payment identifier.
     * @property billId Bill identifier.
     * @property customerId Customer identifier.
     * @property failureReason Reason for failure.
     * @property failedAt Timestamp when failed.
     */
    @Serializable
    @SerialName("com.polecatworks.billing.PaymentFailed")
    data class PaymentFailed(
        val paymentId: String,
        val billId: String,
        val customerId: String,
        val failureReason: String,
        val failedAt: Long,
    ) : Event()

    /**
     * Represents an aggregated state of Billing events.
     *
     * @property bill The associated Bill event.
     * @property paymentRequests List of payment requests.
     * @property lastPaymentFailed The last failed payment event.
     * @property errored Flag indicating if the aggregate is in an errored state.
     */
    @Serializable
    @SerialName("com.polecatworks.billing.BillAggregate")
    data class BillAggregate(
        val bill: Bill? = null,
        val paymentRequests: List<PaymentRequest> = emptyList(),
        val lastPaymentFailed: PaymentFailed? = null,
        @AvroDefault("false")
        val errored: Boolean = false,
    ) : Event()

    companion object {
        /**
         * Returns all sealed subclasses of Event.
         */
        fun subClasses(): List<KClass<out Event>> = Event::class.sealedSubclasses
    }
}
