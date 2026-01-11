package com.polecatworks.kotlin.k8smicro.eventSerde

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable
sealed class Event {
    @Serializable
    @SerialName("com.polecatworks.chaser.Chaser")
    data class Chaser(
        val name: String,
        val id: String,
        val sent: Long,
        val ttl: Long,
        val previous: Long?,
    ) : Event()

    @Serializable
    @SerialName("com.polecatworks.chaser.Aggregate")
    data class Aggregate(
        val names: List<String>,
        val count: Long,
        val latest: Long?,
        val longest: Long?,
    ) : Event()

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

    @Serializable
    @SerialName("com.polecatworks.billing.PaymentFailed")
    data class PaymentFailed(
        val paymentId: String,
        val billId: String,
        val customerId: String,
        val failureReason: String,
        val failedAt: Long,
    ) : Event()

    @Serializable
    @SerialName("com.polecatworks.billing.BillAggregate")
    data class BillAggregate(
        val bill: Bill? = null,
        val paymentRequests: List<PaymentRequest> = emptyList(),
        val lastPaymentFailed: PaymentFailed? = null,
    ) : Event()

    companion object {
        fun subClasses(): List<KClass<out Event>> = Event::class.sealedSubclasses
    }
}
